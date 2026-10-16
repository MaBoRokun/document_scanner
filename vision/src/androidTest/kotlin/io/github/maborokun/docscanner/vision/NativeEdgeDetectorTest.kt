package io.github.maborokun.docscanner.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.maborokun.docscanner.core.common.DefaultAppDispatchers
import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.vision.Frame
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer
import kotlin.math.hypot

/** Runs the real JNI pipeline on the generated synthetic pages (see tools/generate_synthetic.py). */
@RunWith(AndroidJUnit4::class)
class NativeEdgeDetectorTest {
    private val detector = NativeEdgeDetector(DefaultAppDispatchers)
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets

    private fun load(path: String): Bitmap = assets.open(path).use { BitmapFactory.decodeStream(it) }

    private fun frameOf(
        bitmap: Bitmap,
        rotation: Int = 0,
        padding: Int = 0,
    ): Frame {
        val stride = bitmap.width + padding
        val buffer = ByteBuffer.allocateDirect(stride * bitmap.height)
        val row = IntArray(bitmap.width)
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in 0 until bitmap.width) {
                val c = row[x]
                // ITU-R BT.601 luma, what a camera's Y plane approximates.
                buffer.put(
                    y * stride + x,
                    (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)).toInt().toByte(),
                )
            }
        }
        return Frame(bitmap.width, bitmap.height, stride, buffer, rotation)
    }

    private fun detect(frame: Frame): DetectionResult = runBlocking { detector.detect(frame) }

    private fun expectedCorners(name: String): List<Pair<Double, Double>> {
        val json = JSONObject(assets.open("pages/corners.json").bufferedReader().use { it.readText() })
        val array = json.getJSONArray(name)
        return List(array.length()) { array.getJSONArray(it).let { p -> p.getDouble(0) to p.getDouble(1) } }
    }

    private fun maxCornerError(
        result: DetectionResult,
        expected: List<Pair<Double, Double>>,
    ): Double {
        val quad = requireNotNull(result.quad) { "no quad detected" }
        return quad.corners.zip(expected).maxOf { (found, want) -> hypot(found.x - want.first, found.y - want.second) }
    }

    @Test
    fun synthetic_pages_are_found_within_tolerance() {
        for (i in 0 until PAGE_COUNT) {
            val name = "page_$i.png"
            val result = detect(frameOf(load("pages/$name")))
            val error = maxCornerError(result, expectedCorners(name))
            Log.i(
                "EdgeDetectorTest",
                "$name corner error=$error confidence=${result.confidence} skew=${result.skewDegrees} blur=${result.blurScore}",
            )
            assertTrue("$name: corner error $error exceeds $TOLERANCE", error <= TOLERANCE)
            assertTrue("$name: confidence ${result.confidence} too low", result.confidence >= MIN_CONFIDENCE)
        }
    }

    @Test
    fun row_padding_in_the_buffer_is_ignored() {
        val bitmap = load("pages/page_0.png")
        val plain = detect(frameOf(bitmap))
        val padded = detect(frameOf(bitmap, padding = 48))
        assertEquals(requireNotNull(plain.quad).topLeft.x, requireNotNull(padded.quad).topLeft.x, 1e-4f)
        assertEquals(plain.confidence, padded.confidence, 1e-4f)
    }

    @Test
    fun rotation_is_applied_to_the_reported_quad() {
        val bitmap = load("pages/page_1.png")
        val upright = requireNotNull(detect(frameOf(bitmap)).quad)
        val rotated = detect(frameOf(bitmap, rotation = 90))
        val expected = upright.rotatedClockwise(90)
        val quad = requireNotNull(rotated.quad)
        quad.corners.zip(expected.corners).forEach { (a, b) -> assertEquals(b.x, a.x, 1e-3f) }
        assertEquals(bitmap.height, rotated.frameSize.width)
        assertEquals(bitmap.width, rotated.frameSize.height)
    }

    @Test
    fun a_flat_frame_has_no_document() {
        val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GRAY) }
        val result = detect(frameOf(bitmap))
        assertNull(result.quad)
        assertEquals(0f, result.confidence, 0f)
    }

    @Test
    fun tiny_frames_are_rejected_without_crashing() {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        assertNull(detect(frameOf(bitmap)).quad)
    }

    @Test
    fun a_sharp_page_scores_higher_than_a_blurred_one() {
        val sharp = load("pages/page_2.png")
        val blurred =
            Bitmap.createScaledBitmap(
                Bitmap.createScaledBitmap(sharp, 40, 30, true),
                sharp.width,
                sharp.height,
                true,
            )
        val sharpScore = detect(frameOf(sharp)).blurScore
        val blurScore = detect(frameOf(blurred)).blurScore
        Log.i("EdgeDetectorTest", "blur sharp=$sharpScore blurred=$blurScore")
        assertFalse("sharp $sharpScore should beat blurred $blurScore", sharpScore <= blurScore)
    }

    private companion object {
        const val PAGE_COUNT = 8
        const val TOLERANCE = 0.02
        const val MIN_CONFIDENCE = 0.6f
    }
}

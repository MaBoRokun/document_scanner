package io.github.maborokun.docscanner.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.maborokun.docscanner.core.common.DefaultAppDispatchers
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs
import kotlin.math.hypot

@RunWith(AndroidJUnit4::class)
class NativePageProcessorTest {
    private val ops = NativeBitmapOps()
    private val processor = NativePageProcessor(DefaultAppDispatchers, ops)
    private val context = InstrumentationRegistry.getInstrumentation().context

    private fun load(path: String): Bitmap =
        context.assets
            .open(path)
            .use { BitmapFactory.decodeStream(it) }
            .copy(Bitmap.Config.ARGB_8888, true)

    private fun cornersOf(name: String): Quad {
        val json =
            JSONObject(
                context.assets
                    .open("pages/corners.json")
                    .bufferedReader()
                    .use { it.readText() },
            )
        val a = json.getJSONArray(name)
        val p = List(4) { Point(a.getJSONArray(it).getDouble(0).toFloat(), a.getJSONArray(it).getDouble(1).toFloat()) }
        return Quad(p[0], p[1], p[2], p[3])
    }

    private fun luma(color: Int) =
        (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()

    @Test
    fun warpFlattensTheSyntheticPage() {
        for (i in 0 until PAGES) {
            val name = "page_$i.png"
            val flat = ops.warpBitmap(load("pages/$name"), cornersOf(name), 600, 800)

            // The paper colour reaches all four corners and the border strips: no desk bleeds in.
            val strips = listOf(5 to 5, 594 to 5, 594 to 794, 5 to 794, 300 to 4, 300 to 795, 4 to 400, 595 to 400)
            for ((x, y) in strips) {
                val c = flat.getPixel(x, y)
                assertTrue("$name ($x,$y) should be paper but is ${Integer.toHexString(c)}", luma(c) > 200)
            }
            // The first text bar of the generator sits at y 120..136, from x=40; the gap below it is paper.
            assertTrue("$name: bar should be dark", luma(flat.getPixel(100, 128)) < 100)
            assertTrue("$name: gap should be paper", luma(flat.getPixel(100, 147)) > 200)
        }
    }

    @Test
    fun warpRejectsADegenerateQuad() {
        val line = Quad(Point(0.1f, 0.1f), Point(0.5f, 0.1f), Point(0.9f, 0.1f), Point(0.3f, 0.1f))
        val failed = runCatching { ops.warpBitmap(load("pages/page_0.png"), line, 100, 100) }
        assertTrue(failed.isFailure)
    }

    @Test
    fun contrastWhitensUnevenLightingAndKeepsText() {
        val lit = unevenlyLitPage()
        ops.enhanceBitmap(lit, EnhanceMode.Contrast)
        assertTrue("left paper", luma(lit.getPixel(10, 10)) >= 235)
        assertTrue("right paper", luma(lit.getPixel(lit.width - 10, 10)) >= 235)
        assertTrue("text stays dark", luma(lit.getPixel(100, 100)) < 80)
    }

    @Test
    fun blackAndWhiteProducesOnlyBlackAndWhite() {
        val lit = unevenlyLitPage()
        ops.enhanceBitmap(lit, EnhanceMode.BlackWhite)
        val pixels =
            IntArray(
                lit.width * lit.height,
            ).also { lit.getPixels(it, 0, lit.width, 0, 0, lit.width, lit.height) }
        assertTrue(pixels.all { it == Color.BLACK || it == Color.WHITE })
        assertEquals(Color.BLACK, lit.getPixel(100, 100))
        assertEquals(Color.WHITE, lit.getPixel(10, 10))
        assertEquals(Color.WHITE, lit.getPixel(lit.width - 10, 10))
    }

    @Test
    fun enhanceNoneLeavesPixelsAlone() {
        val page = unevenlyLitPage()
        val before = page.getPixel(10, 10)
        ops.enhanceBitmap(page, EnhanceMode.None)
        assertEquals(before, page.getPixel(10, 10))
    }

    @Test
    fun fileLevelWarpAndEnhanceWriteDecodableJpegs() =
        runBlocking {
            val cache = context.cacheDir
            val input =
                File(cache, "in.jpg").apply {
                    outputStream().use { load("pages/page_0.png").compress(Bitmap.CompressFormat.JPEG, 95, it) }
                }
            val flat = File(cache, "flat.jpg")
            val enhanced = File(cache, "enhanced.jpg")

            val size = processor.warp(input.path, cornersOf("page_0.png"), flat.path)
            processor.enhance(flat.path, EnhanceMode.BlackWhite, enhanced.path)

            val decoded = requireNotNull(BitmapFactory.decodeFile(flat.path))
            assertEquals(size.width, decoded.width)
            assertEquals(size.height, decoded.height)
            // The flat page takes the longer of each pair of opposite quad edges, in source pixels (1280x960).
            val q = cornersOf("page_0.png")
            val expectedWidth = maxOf(q.topLeft.distanceToPx(q.topRight), q.bottomLeft.distanceToPx(q.bottomRight))
            val expectedHeight = maxOf(q.topLeft.distanceToPx(q.bottomLeft), q.topRight.distanceToPx(q.bottomRight))
            assertTrue(abs(size.width - expectedWidth) <= 2f)
            assertTrue(abs(size.height - expectedHeight) <= 2f)
            assertNotNull(BitmapFactory.decodeFile(enhanced.path))
        }

    private fun Point.distanceToPx(other: Point) = hypot((x - other.x) * SOURCE_WIDTH, (y - other.y) * SOURCE_HEIGHT)

    /** White page with a left-to-right brightness ramp (255 -> 150) and one dark text block. */
    private fun unevenlyLitPage(): Bitmap {
        val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        for (x in 0 until bitmap.width) {
            val level = 255 - x * 105 / bitmap.width
            for (y in 0 until bitmap.height) bitmap.setPixel(x, y, Color.rgb(level, level, level))
        }
        for (x in 80 until 160) for (y in 90 until 110) bitmap.setPixel(x, y, Color.rgb(20, 20, 20))
        return bitmap
    }

    private companion object {
        const val PAGES = 8
        const val SOURCE_WIDTH = 1280f
        const val SOURCE_HEIGHT = 960f
    }
}

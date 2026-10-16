package io.github.maborokun.docscanner.vision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Bitmap-level native image operations; stateless, so concurrent calls are safe. */
internal object NativePageBridge {
    init {
        System.loadLibrary("docscanner_vision")
    }

    /** [quad] holds 8 source-pixel coordinates (top-left, top-right, bottom-right, bottom-left). */
    external fun nativeWarp(
        src: Bitmap,
        quad: FloatArray,
        dst: Bitmap,
    ): Boolean

    /** [mode] 1 = contrast, 2 = black and white; edits [bitmap] in place. */
    external fun nativeEnhance(
        bitmap: Bitmap,
        mode: Int,
    ): Boolean
}

/** [BitmapOps] over the C++ in `page_ops.cpp` and the platform codecs. */
class NativeBitmapOps : BitmapOps {
    override fun warpBitmap(
        source: Bitmap,
        quad: Quad,
        width: Int,
        height: Int,
    ): Bitmap {
        val pixels = quad.corners.flatMap { listOf(it.x * source.width, it.y * source.height) }.toFloatArray()
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (!NativePageBridge.nativeWarp(source, pixels, flat)) {
            flat.recycle()
            error("Perspective warp failed for a ${source.width}x${source.height} image")
        }
        return flat
    }

    override fun enhanceBitmap(
        bitmap: Bitmap,
        mode: EnhanceMode,
    ) {
        val code =
            when (mode) {
                EnhanceMode.None -> return
                EnhanceMode.Contrast -> MODE_CONTRAST
                EnhanceMode.BlackWhite -> MODE_BLACK_WHITE
            }
        check(NativePageBridge.nativeEnhance(bitmap, code)) { "Enhancement failed" }
    }

    /** Long edges of the quad in pixels, so the flat page keeps roughly the resolution it had, capped for memory. */
    override fun outputSize(
        quad: Quad,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Int, Int> {
        fun edge(
            a: Point,
            b: Point,
        ) = hypot((a.x - b.x) * sourceWidth, (a.y - b.y) * sourceHeight)

        val width = max(edge(quad.topLeft, quad.topRight), edge(quad.bottomLeft, quad.bottomRight))
        val height = max(edge(quad.topLeft, quad.bottomLeft), edge(quad.topRight, quad.bottomRight))
        val scale = minOf(1f, MAX_OUTPUT_SIDE / max(width, height))
        return max(MIN_OUTPUT_SIDE, (width * scale).roundToInt()) to max(MIN_OUTPUT_SIDE, (height * scale).roundToInt())
    }

    /** Decodes with a power-of-two downsample so the long side stays near [MAX_DECODE_SIDE]. */
    override fun decode(path: String): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Not a decodable image: $path" }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DECODE_SIDE) sample *= 2
        val options =
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inMutable = true
            }
        return requireNotNull(BitmapFactory.decodeFile(path, options)) { "Not a decodable image: $path" }
    }

    override fun save(
        bitmap: Bitmap,
        path: String,
    ) {
        File(path).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) }
    }

    private companion object {
        const val MODE_CONTRAST = 1
        const val MODE_BLACK_WHITE = 2
        const val MAX_DECODE_SIDE = 4096
        const val MAX_OUTPUT_SIDE = 3000f
        const val MIN_OUTPUT_SIDE = 16
        const val JPEG_QUALITY = 92
    }
}

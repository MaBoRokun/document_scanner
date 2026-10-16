package io.github.maborokun.docscanner.vision

import android.graphics.Bitmap
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.processing.PageProcessor
import kotlinx.coroutines.withContext

/** [PageProcessor] for files: decode, flatten or enhance with [ops], encode; runs on `dispatchers.processing`. */
class NativePageProcessor(
    private val dispatchers: AppDispatchers,
    private val ops: BitmapOps = NativeBitmapOps(),
) : PageProcessor {
    override suspend fun warp(
        input: String,
        quad: Quad,
        output: String,
    ): FrameSize =
        withContext(dispatchers.processing) {
            val source = ops.decode(input)
            try {
                val (width, height) = ops.outputSize(quad, source.width, source.height)
                val flat = ops.warpBitmap(source, quad, width, height)
                try {
                    ops.save(flat, output)
                    FrameSize(flat.width, flat.height)
                } finally {
                    flat.recycle()
                }
            } finally {
                source.recycle()
            }
        }

    override suspend fun enhance(
        input: String,
        mode: EnhanceMode,
        output: String,
    ) {
        withContext(dispatchers.processing) {
            val bitmap = ops.decode(input)
            try {
                ops.enhanceBitmap(bitmap, mode)
                ops.save(bitmap, output)
            } finally {
                bitmap.recycle()
            }
        }
    }
}

/** The bitmap-level image operations the processor and the renderer are built from. */
interface BitmapOps {
    /** Decodes [path] downsampled so the long side stays near a few thousand pixels; the bitmap is mutable. */
    fun decode(path: String): Bitmap

    /** Flattens [quad] (normalized to [source]) into a new [width] x [height] bitmap. */
    fun warpBitmap(
        source: Bitmap,
        quad: Quad,
        width: Int,
        height: Int,
    ): Bitmap

    /** Enhances [bitmap] in place; it must be ARGB_8888 and mutable. */
    fun enhanceBitmap(
        bitmap: Bitmap,
        mode: EnhanceMode,
    )

    /** Size in pixels of the flat page for [quad]: its long edges, capped for memory. */
    fun outputSize(
        quad: Quad,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Pair<Int, Int>

    /** Writes [bitmap] as a JPEG. */
    fun save(
        bitmap: Bitmap,
        path: String,
    )
}

package io.github.maborokun.docscanner.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.Stroke
import io.github.maborokun.docscanner.domain.processing.PageRenderer
import kotlinx.coroutines.withContext

/**
 * [PageRenderer] on top of [BitmapOps]: crop and enhance natively, then brightness, tint and pen strokes
 * with the platform canvas. Runs on `dispatchers.processing`.
 */
class NativePageRenderer(
    private val ops: BitmapOps,
    private val dispatchers: AppDispatchers,
) : PageRenderer {
    override suspend fun render(
        original: String,
        quad: Quad,
        edit: PageEdit,
        output: String,
    ): FrameSize =
        withContext(dispatchers.processing) {
            var bitmap = ops.decode(original)
            try {
                if (quad != Quad.FULL) {
                    val (width, height) = ops.outputSize(quad, bitmap.width, bitmap.height)
                    val flat = ops.warpBitmap(bitmap, quad, width, height)
                    bitmap.recycle()
                    bitmap = flat
                }
                ops.enhanceBitmap(bitmap, edit.mode)
                if (edit.hasColorChange) bitmap = recolored(bitmap, edit)
                drawStrokes(bitmap, edit.strokes)
                ops.save(bitmap, output)
                FrameSize(bitmap.width, bitmap.height)
            } finally {
                bitmap.recycle()
            }
        }

    /** A copy of [source] with brightness and tint applied; [source] is recycled. */
    private fun recolored(
        source: Bitmap,
        edit: PageEdit,
    ): Bitmap {
        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint =
            Paint(Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter =
                    ColorMatrixColorFilter(ColorMatrix(edit.colorMatrix()))
            }
        Canvas(result).drawBitmap(source, 0f, 0f, paint)
        source.recycle()
        return result
    }

    private fun drawStrokes(
        bitmap: Bitmap,
        strokes: List<Stroke>,
    ) {
        if (strokes.isEmpty()) return
        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()
        for (stroke in strokes.filter { it.points.isNotEmpty() }) {
            val paint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = stroke.color
                    strokeWidth = maxOf(1f, stroke.width * width)
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                    style = Paint.Style.STROKE
                }
            val first = stroke.points.first()
            if (stroke.points.size == 1) {
                canvas.drawPoint(first.x * width, first.y * height, paint)
            } else {
                val path = Path().apply { moveTo(first.x * width, first.y * height) }
                stroke.points.drop(1).forEach { path.lineTo(it.x * width, it.y * height) }
                canvas.drawPath(path, paint)
            }
        }
    }
}

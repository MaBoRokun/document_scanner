package io.github.maborokun.docscanner.core.designsystem

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import io.github.maborokun.docscanner.domain.model.Point

/**
 * Where an image with width/height ratio [imageAspect] lands when it is fitted (centred, uncropped) into a
 * container of the given pixel size, leaving [inset] pixels free on every side. Maps between normalized image
 * coordinates and container pixels.
 */
class ImageViewport(
    containerWidth: Float,
    containerHeight: Float,
    imageAspect: Float,
    /** Free space kept on every side, so handles drawn on the image border are never clipped by the container. */
    inset: Float = 0f,
) {
    val rect: Rect =
        run {
            val availableWidth = containerWidth - 2 * inset
            val availableHeight = containerHeight - 2 * inset
            val containerAspect = availableWidth / availableHeight
            val size =
                if (containerAspect > imageAspect) {
                    Size(availableHeight * imageAspect, availableHeight)
                } else {
                    Size(availableWidth, availableWidth / imageAspect)
                }
            Rect(
                Offset((containerWidth - size.width) / 2f, (containerHeight - size.height) / 2f),
                size,
            )
        }

    fun toScreen(point: Point): Offset = Offset(rect.left + point.x * rect.width, rect.top + point.y * rect.height)

    /** Inverse of [toScreen], clamped to the image so a drag cannot leave it. */
    fun toImage(offset: Offset): Point =
        Point(
            ((offset.x - rect.left) / rect.width).coerceIn(0f, 1f),
            ((offset.y - rect.top) / rect.height).coerceIn(0f, 1f),
        )
}

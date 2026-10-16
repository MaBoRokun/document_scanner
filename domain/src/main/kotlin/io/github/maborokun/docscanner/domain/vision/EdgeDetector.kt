package io.github.maborokun.docscanner.domain.vision

import io.github.maborokun.docscanner.domain.model.DetectionResult
import java.nio.ByteBuffer

/**
 * One camera frame, luminance only. [luma] holds [height] rows of [rowStride] bytes, of which [width] are pixels.
 * [rotationDegrees] is the clockwise rotation that makes the frame upright; results are reported upright.
 */
class Frame(
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val luma: ByteBuffer,
    val rotationDegrees: Int = 0,
) {
    init {
        require(width > 0 && height > 0) { "Empty frame: ${width}x$height" }
        require(rowStride >= width) { "rowStride $rowStride is smaller than width $width" }
        require(luma.remaining() >= rowStride * (height - 1) + width) { "Luma buffer is too small" }
        require(rotationDegrees in ROTATIONS) { "rotationDegrees must be 0, 90, 180 or 270: $rotationDegrees" }
    }

    private companion object {
        val ROTATIONS = setOf(0, 90, 180, 270)
    }
}

/** Finds the page quadrilateral in a frame. Implementations must be safe to call from several threads. */
interface EdgeDetector {
    suspend fun detect(frame: Frame): DetectionResult
}

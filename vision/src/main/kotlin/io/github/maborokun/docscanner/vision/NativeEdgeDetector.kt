package io.github.maborokun.docscanner.vision

import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.vision.EdgeDetector
import io.github.maborokun.docscanner.domain.vision.Frame
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

/** JNI entry point; one stateless native function, so concurrent calls are safe. */
internal object NativeBridge {
    init {
        System.loadLibrary("docscanner_vision")
    }

    /** [out] receives 8 corner coordinates, confidence, skewDegrees and blurScore; [luma] must be direct. */
    external fun nativeDetect(
        luma: ByteBuffer,
        width: Int,
        height: Int,
        rowStride: Int,
        out: FloatArray,
    ): Boolean
}

/** [EdgeDetector] backed by the C++ pipeline in `src/main/cpp`. */
class NativeEdgeDetector(
    private val dispatchers: AppDispatchers,
) : EdgeDetector {
    override suspend fun detect(frame: Frame): DetectionResult =
        withContext(dispatchers.default) { detectBlocking(frame) }

    private fun detectBlocking(frame: Frame): DetectionResult {
        val quarterTurn = frame.rotationDegrees == 90 || frame.rotationDegrees == 270
        val upright = if (quarterTurn) FrameSize(frame.height, frame.width) else FrameSize(frame.width, frame.height)
        val out = FloatArray(RESULT_SIZE)
        val found = NativeBridge.nativeDetect(frame.luma.asDirect(), frame.width, frame.height, frame.rowStride, out)
        val blur = out[BLUR_INDEX]
        if (!found) return DetectionResult(null, 0f, 0f, blur, upright)
        val quad =
            Quad
                .fromUnordered(List(CORNERS) { Point(out[2 * it], out[2 * it + 1]) })
                .rotatedClockwise(frame.rotationDegrees)
        return DetectionResult(quad, out[CONFIDENCE_INDEX], out[SKEW_INDEX], blur, upright)
    }

    /** The native side reads through a raw pointer, which only direct buffers have. */
    private fun ByteBuffer.asDirect(): ByteBuffer {
        if (isDirect) return this
        val copy = ByteBuffer.allocateDirect(remaining())
        copy.put(duplicate())
        copy.flip()
        return copy
    }

    private companion object {
        const val CORNERS = 4
        const val CONFIDENCE_INDEX = 8
        const val SKEW_INDEX = 9
        const val BLUR_INDEX = 10
        const val RESULT_SIZE = 11
    }
}

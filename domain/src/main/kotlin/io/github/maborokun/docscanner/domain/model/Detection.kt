package io.github.maborokun.docscanner.domain.model

data class FrameSize(
    val width: Int,
    val height: Int,
)

data class DetectionResult(
    val quad: Quad?,
    val confidence: Float,
    val skewDegrees: Float,
    val blurScore: Float,
    val frameSize: FrameSize,
)

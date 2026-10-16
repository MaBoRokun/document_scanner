package io.github.maborokun.docscanner.domain.vision

import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan

/**
 * Low-pass filter settings for detections. [alpha] is the weight of each new frame (1 = no smoothing),
 * a corner that moves more than [jumpDistance] (normalized) snaps to the new position instead of gliding,
 * and a lost page is held for [maxMisses] frames before it disappears.
 */
data class SmoothingConfig(
    val alpha: Float = 0.4f,
    val jumpDistance: Float = 0.15f,
    val maxMisses: Int = 5,
)

internal data class SmoothingState(
    val output: DetectionResult? = null,
    val misses: Int = 0,
)

/** One filter step; pure, so the flow below needs no shared mutable state. */
internal fun smoothStep(
    state: SmoothingState,
    input: DetectionResult,
    config: SmoothingConfig,
): SmoothingState = if (input.quad == null) hold(state, input, config) else blend(state.output, input, config)

/** No page in this frame: keep showing the previous one for a few frames, then give up. */
private fun hold(
    state: SmoothingState,
    input: DetectionResult,
    config: SmoothingConfig,
): SmoothingState {
    val misses = state.misses + 1
    val previous = state.output
    return if (previous?.quad != null && misses <= config.maxMisses) {
        SmoothingState(previous.copy(frameSize = input.frameSize), misses)
    } else {
        SmoothingState(input, misses)
    }
}

private fun blend(
    previous: DetectionResult?,
    input: DetectionResult,
    config: SmoothingConfig,
): SmoothingState {
    val inQuad = requireNotNull(input.quad)
    val prevQuad = previous?.quad
    val snap =
        prevQuad == null ||
            previous.frameSize != input.frameSize ||
            maxCornerDistance(prevQuad, inQuad) > config.jumpDistance
    if (snap) return SmoothingState(input, 0)
    val a = config.alpha
    return SmoothingState(
        DetectionResult(
            quad = lerp(prevQuad, inQuad, a),
            confidence = lerp(previous.confidence, input.confidence, a),
            skewDegrees = lerp(previous.skewDegrees, input.skewDegrees, a),
            blurScore = lerp(previous.blurScore, input.blurScore, a),
            frameSize = input.frameSize,
        ),
        0,
    )
}

/** Smooths a stream of raw detections so the overlay glides instead of jittering. */
fun Flow<DetectionResult>.smoothed(config: SmoothingConfig = SmoothingConfig()): Flow<DetectionResult> =
    scan(SmoothingState()) { state, input -> smoothStep(state, input, config) }
        .drop(1)
        .map { requireNotNull(it.output) }

private fun maxCornerDistance(
    a: Quad,
    b: Quad,
): Float = a.corners.zip(b.corners).maxOf { (p, q) -> p.distanceTo(q) }

private fun lerp(
    from: Float,
    to: Float,
    t: Float,
) = from + (to - from) * t

private fun lerp(
    from: Quad,
    to: Quad,
    t: Float,
): Quad {
    fun mix(
        p: Point,
        q: Point,
    ) = Point(lerp(p.x, q.x, t), lerp(p.y, q.y, t))
    return Quad(
        mix(from.topLeft, to.topLeft),
        mix(from.topRight, to.topRight),
        mix(from.bottomRight, to.bottomRight),
        mix(from.bottomLeft, to.bottomLeft),
    )
}

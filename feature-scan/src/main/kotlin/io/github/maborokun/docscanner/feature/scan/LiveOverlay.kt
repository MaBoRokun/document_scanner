package io.github.maborokun.docscanner.feature.scan

import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.PaperFormat
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.QuadMeasurement
import io.github.maborokun.docscanner.domain.model.measureQuad
import io.github.maborokun.docscanner.domain.vision.smoothed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** What the live overlay shows. [quad] is null while no page is found and the user has not drawn one. */
data class OverlayState(
    val quad: Quad? = null,
    val frameSize: FrameSize? = null,
    val confidence: Float = 0f,
    /** True when the user dragged a corner; automatic tracking is then ignored until [LiveOverlay.resetQuad]. */
    val manual: Boolean = false,
    val paper: PaperFormat? = null,
    val measurement: QuadMeasurement? = null,
)

/**
 * The state behind the live viewfinder overlay: the detector's latest page, a corner the user moved by hand and
 * the paper format to measure against. Kept apart from the screen's own flow (capture, review, saving) so each
 * changes for its own reasons.
 */
class LiveOverlay(
    scope: CoroutineScope,
) {
    private val detection = MutableStateFlow<DetectionResult?>(null)
    private val manualQuad = MutableStateFlow<Quad?>(null)
    private val paper = MutableStateFlow<PaperFormat?>(null)

    val overlay: StateFlow<OverlayState> =
        combine(detection, manualQuad, paper, ::overlayOf)
            .stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), OverlayState())

    /** Collects raw detections (smoothed here) until cancelled; call from a lifecycle-bound effect. */
    suspend fun track(source: Flow<DetectionResult>) {
        source.smoothed().collect { result -> detection.update { result } }
    }

    fun editQuad(quad: Quad) = manualQuad.update { quad }

    fun resetQuad() = manualQuad.update { null }

    fun selectPaper(format: PaperFormat?) = paper.update { format }

    private fun overlayOf(
        detected: DetectionResult?,
        manual: Quad?,
        format: PaperFormat?,
    ): OverlayState {
        val frame = detected?.frameSize
        val quad = manual ?: detected?.quad
        if (frame == null || quad == null) return OverlayState(frameSize = frame, paper = format)
        // A corner the user placed is as trustworthy as it gets.
        val confidence = if (manual != null) 1f else detected.confidence
        val measurement = measureQuad(quad, frame, format, confidence, detected.skewDegrees, detected.blurScore)
        return OverlayState(quad, frame, confidence, manual != null, format, measurement)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

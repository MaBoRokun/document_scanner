package io.github.maborokun.docscanner.feature.scan

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.maborokun.docscanner.core.common.AppDispatchers
import io.github.maborokun.docscanner.core.common.catching
import io.github.maborokun.docscanner.domain.model.DetectionResult
import io.github.maborokun.docscanner.domain.model.PaperFormat
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.StoredImage
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import io.github.maborokun.docscanner.domain.settings.QuickScanAvailability
import io.github.maborokun.docscanner.domain.settings.ScanEngine
import io.github.maborokun.docscanner.domain.settings.SettingsRepository
import io.github.maborokun.docscanner.domain.usecase.ScanDocument
import io.github.maborokun.docscanner.domain.vision.EdgeDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ScanUiState {
    /** Live camera. */
    data object Ready : ScanUiState

    /** Copying the capture, or running warp, enhancement and OCR. */
    data object Saving : ScanUiState

    /** The captured photo with an adjustable page outline, before it is turned into a document. */
    data class Reviewing(
        val image: StoredImage,
        val quad: Quad,
        val mode: EnhanceMode = EnhanceMode.Contrast,
    ) : ScanUiState

    data class Saved(
        val documentId: Long,
    ) : ScanUiState

    /** [message] is a string resource that takes [detail] (the cause, or null if unknown). */
    data class Failed(
        @StringRes val message: Int,
        val detail: String?,
    ) : ScanUiState
}

enum class ScanMode { Loading, Camera, QuickScan }

/** Outline offered in the review step when nothing was detected: the whole photo, slightly inset. */
private val DEFAULT_QUAD = Quad(Point(0.05f, 0.05f), Point(0.95f, 0.05f), Point(0.95f, 0.95f), Point(0.05f, 0.95f))

class ScanViewModel(
    private val store: PageImageStore,
    private val scanDocument: ScanDocument,
    private val dispatchers: AppDispatchers,
    /** Handed to the camera session, which feeds it the analysis frames. */
    val edgeDetector: EdgeDetector,
    private val settings: SettingsRepository,
    private val quickScan: QuickScanAvailability,
    /** When set, the saved page is added to this document instead of creating a new one. */
    val appendTo: Long? = null,
) : ViewModel() {
    private val state = MutableStateFlow<ScanUiState>(ScanUiState.Ready)
    val uiState: StateFlow<ScanUiState> = state.asStateFlow()

    private val live = LiveOverlay(viewModelScope)

    /** Which scanner to show; [ScanMode.Loading] until the stored setting has been read. */
    val mode: StateFlow<ScanMode> =
        settings.settings
            .map {
                if (it.engine == ScanEngine.QuickScan &&
                    quickScan.isAvailable()
                ) {
                    ScanMode.QuickScan
                } else {
                    ScanMode.Camera
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ScanMode.Loading)

    init {
        // The default paper from settings is only a starting point; choosing one on screen wins afterwards.
        viewModelScope.launch { live.selectPaper(settings.settings.first().defaultPaper) }
    }

    val overlay: StateFlow<OverlayState> = live.overlay

    /** Collects raw detections until cancelled; call from a lifecycle-bound effect. */
    suspend fun trackDetections(source: Flow<DetectionResult>) = live.track(source)

    fun onQuadEdited(quad: Quad) = live.editQuad(quad)

    fun resetQuad() = live.resetQuad()

    fun selectPaper(format: PaperFormat?) = live.selectPaper(format)

    /**
     * [capture] is a temporary JPEG. It is copied upright into private storage and removed; the page outline
     * seen live at this moment becomes the starting point for the review.
     */
    fun onCaptured(capture: File) {
        val outline = overlay.value.quad ?: DEFAULT_QUAD
        state.value = ScanUiState.Saving
        viewModelScope.launch {
            val staged = catching { store.import(capture.toURI().toString()) }
            withContext(dispatchers.io) { capture.delete() }
            state.value =
                staged.fold(
                    onSuccess = { ScanUiState.Reviewing(it, outline) },
                    onFailure = { fail(R.string.error_read_photo, it) },
                )
        }
    }

    /** Pages scanned by Google's scanner (content URIs): each one runs through the normal pipeline, OCR included. */
    fun onQuickScanPages(sources: List<String>) {
        state.value = ScanUiState.Saving
        viewModelScope.launch {
            val result =
                catching {
                    var documentId = appendTo
                    for (source in sources) {
                        documentId = scanDocument(store.import(source), appendTo = documentId)
                    }
                    requireNotNull(documentId) { "The scanner returned no pages" }
                }
            state.value =
                result.fold(onSuccess = { ScanUiState.Saved(it) }, onFailure = { fail(R.string.error_save_scan, it) })
        }
    }

    fun onCaptureFailed(
        @StringRes message: Int,
        detail: String?,
    ) {
        state.value = ScanUiState.Failed(message, detail)
    }

    fun onReviewQuadChanged(quad: Quad) = updateReview { it.copy(quad = quad) }

    fun onModeSelected(mode: EnhanceMode) = updateReview { it.copy(mode = mode) }

    /** Runs the pipeline for the reviewed photo; ends in [ScanUiState.Saved] or [ScanUiState.Failed]. */
    fun onSave() {
        val review = state.value as? ScanUiState.Reviewing ?: return
        state.value = ScanUiState.Saving
        viewModelScope.launch {
            val result = catching { scanDocument(review.image, review.quad, review.mode, appendTo = appendTo) }
            state.value =
                result.fold(onSuccess = { ScanUiState.Saved(it) }, onFailure = { fail(R.string.error_save_scan, it) })
        }
    }

    /** Throws the photo away and goes back to the camera. */
    fun onRetake() {
        val review = state.value as? ScanUiState.Reviewing ?: return
        state.value = ScanUiState.Ready
        viewModelScope.launch { store.delete(listOf(review.image.path)) }
    }

    /** Back to [ScanUiState.Ready] once the UI has handled a result. */
    fun onResultHandled() {
        state.value = ScanUiState.Ready
    }

    private fun updateReview(change: (ScanUiState.Reviewing) -> ScanUiState.Reviewing) =
        state.update { (it as? ScanUiState.Reviewing)?.let(change) ?: it }

    private fun fail(
        @StringRes what: Int,
        error: Throwable,
    ) = ScanUiState.Failed(what, error.message)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

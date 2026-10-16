package io.github.maborokun.docscanner.feature.export

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.maborokun.docscanner.core.common.catching
import io.github.maborokun.docscanner.domain.processing.ExportProgress
import io.github.maborokun.docscanner.domain.processing.ImageExporter
import io.github.maborokun.docscanner.domain.processing.PdfExporter
import io.github.maborokun.docscanner.domain.usecase.PrepareExport
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What to share: whole [documentIds] as one PDF, or one page of the first document as a PDF or as an image. */
data class ExportRequest(
    val documentIds: List<Long>,
    val pageId: Long? = null,
    val asImage: Boolean = false,
)

sealed interface ExportUiState {
    data object Loading : ExportUiState

    data object NotFound : ExportUiState

    data class Exporting(
        val done: Int,
        val total: Int,
    ) : ExportUiState

    data class Ready(
        val path: String,
        val fileName: String,
        val mimeType: String = PDF_MIME,
    ) : ExportUiState

    /** [message] is a string resource that takes [detail] (the cause, or null if unknown). */
    data class Failed(
        @StringRes val message: Int,
        val detail: String?,
    ) : ExportUiState
}

const val PDF_MIME = "application/pdf"
const val IMAGE_MIME = "image/jpeg"

class ExportViewModel(
    private val request: ExportRequest,
    private val prepareExport: PrepareExport,
    private val exporter: PdfExporter,
    private val imageExporter: ImageExporter,
) : ViewModel() {
    private val state = MutableStateFlow<ExportUiState>(ExportUiState.Loading)
    val uiState: StateFlow<ExportUiState> = state.asStateFlow()
    private var job: Job? = null
    private var autoShared = false

    /** Starts the export unless one is running or already finished; safe to call whenever the screen appears. */
    fun start() {
        if (job?.isActive == true || state.value is ExportUiState.Ready) return
        job =
            viewModelScope.launch {
                val source = prepareExport(request.documentIds, request.pageId)
                if (source == null) {
                    state.value = ExportUiState.NotFound
                    return@launch
                }
                state.value = ExportUiState.Exporting(0, source.pages.size)
                catching {
                    if (request.asImage) {
                        val finished = imageExporter.export(source.pages.first(), source.document.title)
                        state.value = ExportUiState.Ready(finished.path, finished.fileName, IMAGE_MIME)
                    } else {
                        exporter.export(source).collect { progress ->
                            state.value =
                                when (progress) {
                                    is ExportProgress.Pages -> ExportUiState.Exporting(progress.done, progress.total)
                                    is ExportProgress.Finished -> ExportUiState.Ready(progress.path, progress.fileName)
                                }
                        }
                    }
                }.onFailure { state.value = ExportUiState.Failed(R.string.error_export, it.message) }
            }
    }

    /** True once: the share sheet opens by itself the first time the file is ready, not again after it is closed. */
    fun takeAutoShare(): Boolean {
        if (autoShared || state.value !is ExportUiState.Ready) return false
        autoShared = true
        return true
    }

    /** Stops a running export; the exporter removes its partial file. */
    fun cancel() {
        job?.cancel()
    }

    fun retry() {
        if (state.value is ExportUiState.Failed) {
            state.value = ExportUiState.Loading
            start()
        }
    }
}

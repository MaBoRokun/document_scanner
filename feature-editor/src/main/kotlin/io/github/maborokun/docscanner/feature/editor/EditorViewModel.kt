package io.github.maborokun.docscanner.feature.editor

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.PageTint
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.Stroke
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.processing.PageRenderer
import io.github.maborokun.docscanner.domain.repository.DocumentReader
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import io.github.maborokun.docscanner.domain.usecase.EditPage
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

enum class EditorTool(
    @StringRes val label: Int,
) {
    Crop(R.string.tool_crop),
    Adjust(R.string.tool_adjust),
    Filters(R.string.tool_filters),
    Draw(R.string.tool_draw),
}

/** A rendered preview image: the page cropped and enhanced, before brightness, tint and drawing. */
data class BaseImage(
    val path: String,
    val width: Int,
    val height: Int,
) {
    val aspect: Float get() = width.toFloat() / height
}

/** Pen sizes as a fraction of the page width. */
enum class PenSize(
    @StringRes val label: Int,
    val width: Float,
) {
    Thin(R.string.pen_thin, THIN_WIDTH),
    Medium(R.string.pen_medium, MEDIUM_WIDTH),
    Thick(R.string.pen_thick, THICK_WIDTH),
}

private const val THIN_WIDTH = 0.004f
private const val MEDIUM_WIDTH = 0.009f
private const val THICK_WIDTH = 0.02f

data class EditorUiState(
    val loading: Boolean = true,
    val notFound: Boolean = false,
    /** The untouched photo, shown while the crop is being adjusted. */
    val originalPath: String? = null,
    val base: BaseImage? = null,
    val rendering: Boolean = false,
    val quad: Quad = Quad.FULL,
    val edit: PageEdit = PageEdit(),
    val tool: EditorTool = EditorTool.Crop,
    val penColor: Int = DEFAULT_PEN_COLOR,
    val penSize: PenSize = PenSize.Medium,
    val canUndo: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
) {
    companion object {
        const val DEFAULT_PEN_COLOR = 0xFFE53935.toInt()
    }
}

private data class Snapshot(
    val quad: Quad,
    val edit: PageEdit,
)

/**
 * Edits one page: every change is applied to a copy of the page's original photo, so nothing is lost until Save.
 * Undo steps back through the changes one at a time.
 */
class EditorViewModel(
    private val documentId: Long,
    private val pageId: Long,
    private val repository: DocumentReader,
    private val editPage: EditPage,
    private val renderer: PageRenderer,
    private val store: PageImageStore,
) : ViewModel() {
    private val state = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = state.asStateFlow()

    private var page: Page? = null
    private val history = ArrayDeque<Snapshot>()
    private var gestureStart: Snapshot? = null
    private var baseJob: Job? = null

    init {
        viewModelScope.launch {
            val found =
                repository
                    .observe(documentId)
                    .first()
                    ?.pages
                    ?.firstOrNull { it.id == pageId }
            if (found == null) {
                state.update { it.copy(loading = false, notFound = true) }
                return@launch
            }
            page = found
            // Pages saved before originals were kept have only a flat image, which then has no crop to adjust.
            val quad = if (found.originalPath != null) found.quad else Quad.FULL
            state.update {
                it.copy(
                    loading = false,
                    originalPath = found.originalPath ?: found.imagePath,
                    quad = quad,
                    edit = found.edit,
                )
            }
            rebase()
        }
    }

    fun selectTool(tool: EditorTool) = state.update { it.copy(tool = tool) }

    fun onPenColor(color: Int) = state.update { it.copy(penColor = color) }

    fun onPenSize(size: PenSize) = state.update { it.copy(penSize = size) }

    fun onErrorShown() = state.update { it.copy(error = null) }

    /** Moves the crop while a corner is dragged; [finishQuad] closes the gesture as one undo step. */
    fun setQuad(quad: Quad) {
        beginChange()
        state.update { it.copy(quad = quad) }
    }

    fun finishQuad() = endChange()

    fun setMode(mode: EnhanceMode) {
        beginChange()
        state.update { it.copy(edit = it.edit.copy(mode = mode)) }
        endChange()
    }

    /** Live brightness while the slider moves; [finishBrightness] closes the gesture as one undo step. */
    fun setBrightness(value: Float) {
        beginChange()
        state.update { it.copy(edit = it.edit.copy(brightness = value)) }
    }

    fun finishBrightness() = endChange()

    fun setTint(tint: PageTint) {
        beginChange()
        state.update { it.copy(edit = it.edit.copy(tint = tint)) }
        endChange()
    }

    fun startStroke(point: Point) {
        beginChange()
        state.update {
            val stroke = Stroke(it.penColor, it.penSize.width, listOf(point))
            it.copy(edit = it.edit.copy(strokes = it.edit.strokes + stroke))
        }
    }

    fun extendStroke(point: Point) {
        state.update {
            val last = it.edit.strokes.lastOrNull() ?: return@update it
            val strokes = it.edit.strokes.dropLast(1) + last.copy(points = last.points + point)
            it.copy(edit = it.edit.copy(strokes = strokes))
        }
    }

    fun finishStroke() = endChange()

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        val current = state.value
        state.update { it.copy(quad = previous.quad, edit = previous.edit, canUndo = history.isNotEmpty()) }
        if (previous.quad != current.quad || previous.edit.mode != current.edit.mode) rebase()
    }

    /** True when there is something to lose by leaving. */
    fun hasChanges(): Boolean = history.isNotEmpty()

    fun save(onDone: () -> Unit) {
        val target = page ?: return
        val current = state.value
        if (current.saving) return
        state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                editPage(target, current.quad, current.edit)
                onDone()
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                state.update {
                    it.copy(
                        saving = false,
                        error = "Could not save the page: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    private fun beginChange() {
        if (gestureStart == null) gestureStart = Snapshot(state.value.quad, state.value.edit)
    }

    private fun endChange() {
        val start = gestureStart ?: return
        gestureStart = null
        val now = state.value
        if (start == Snapshot(now.quad, now.edit)) return
        val geometryChanged = now.quad != start.quad
        val modeChanged = now.edit.mode != start.edit.mode
        // A new crop moves every point of the page, so a drawing made on the old crop would land in the wrong place.
        val edit = if (geometryChanged) now.edit.copy(strokes = emptyList()) else now.edit
        history.addLast(start)
        state.update { it.copy(edit = edit, canUndo = true) }
        if (geometryChanged || modeChanged) rebase()
    }

    /** Renders the preview base for the current crop and enhancement; a newer request replaces an older one. */
    private fun rebase() {
        val original = state.value.originalPath ?: return
        val quad = state.value.quad
        val mode = state.value.edit.mode
        baseJob?.cancel()
        state.update { it.copy(rendering = true) }
        baseJob =
            viewModelScope.launch {
                val output = store.allocatePath()
                var delivered = false
                try {
                    val size = renderer.render(original, quad, PageEdit(mode = mode), output)
                    val old = state.value.base
                    state.update { it.copy(base = BaseImage(output, size.width, size.height), rendering = false) }
                    delivered = true
                    old?.let { store.delete(listOf(it.path)) }
                } catch (e: CancellationException) {
                    throw e
                } catch (
                    @Suppress("TooGenericExceptionCaught") e: Exception,
                ) {
                    state.update { it.copy(rendering = false, error = "Could not prepare the preview: ${e.message}") }
                } finally {
                    if (!delivered) withContext(NonCancellable) { store.delete(listOf(output)) }
                }
            }
    }

    override fun onCleared() {
        // The preview file is private scratch; the scope is already cancelled, so remove it directly.
        state.value.base?.let { File(it.path).delete() }
    }
}

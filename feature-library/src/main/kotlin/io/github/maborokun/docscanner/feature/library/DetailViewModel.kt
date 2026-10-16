package io.github.maborokun.docscanner.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.maborokun.docscanner.core.common.catching
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.receipt.FieldError
import io.github.maborokun.docscanner.domain.receipt.FieldsInput
import io.github.maborokun.docscanner.domain.receipt.FieldsResult
import io.github.maborokun.docscanner.domain.receipt.parseFieldsInput
import io.github.maborokun.docscanner.domain.repository.DocumentReader
import io.github.maborokun.docscanner.domain.repository.DocumentWriter
import io.github.maborokun.docscanner.domain.usecase.DeleteDocument
import io.github.maborokun.docscanner.domain.usecase.RemovePage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailUiState {
    data object Loading : DetailUiState

    data object NotFound : DetailUiState

    data class Content(
        val document: DocumentWithPages,
    ) : DetailUiState
}

/** Outcome of the last "save fields" attempt: which inputs were rejected, and whether the values were stored. */
data class FieldsFormState(
    val errors: Set<FieldError> = emptySet(),
    val saved: Boolean = false,
)

class DetailViewModel(
    private val id: Long,
    private val reader: DocumentReader,
    private val writer: DocumentWriter,
    private val deleteDocument: DeleteDocument,
    private val removePage: RemovePage,
) : ViewModel() {
    private val form = MutableStateFlow(FieldsFormState())
    val formState: StateFlow<FieldsFormState> = form.asStateFlow()

    val uiState: StateFlow<DetailUiState> =
        reader
            .observe(id)
            .map { if (it == null) DetailUiState.NotFound else DetailUiState.Content(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DetailUiState.Loading)

    /** Validates what the user typed and stores it; invalid input is reported in [formState] and nothing is stored. */
    fun saveFields(input: FieldsInput) {
        when (val result = parseFieldsInput(input)) {
            is FieldsResult.Invalid -> form.value = FieldsFormState(errors = result.errors)
            is FieldsResult.Valid ->
                viewModelScope.launch {
                    catching { writer.updateFields(id, result.fields) }
                        .onSuccess { form.value = FieldsFormState(saved = true) }
                }
        }
    }

    fun setCategory(category: DocumentCategory) {
        viewModelScope.launch { catching { writer.updateCategory(id, category) } }
    }

    fun setComment(comment: String) {
        viewModelScope.launch { catching { writer.updateComment(id, comment) } }
    }

    /** The user started typing again: clear the "saved" and error markers. */
    fun onFieldsEdited() {
        if (form.value != FieldsFormState()) form.value = FieldsFormState()
    }

    /** Removes one page; the last page of a document cannot be removed (delete the document instead). */
    fun removePage(pageId: Long) {
        viewModelScope.launch { catching { removePage.invoke(pageId) } }
    }

    /** Deletes the document and calls [onDone] on success so the screen can pop. */
    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            catching { deleteDocument(id) }.onSuccess { onDone() }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

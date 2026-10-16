package io.github.maborokun.docscanner.feature.library

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.maborokun.docscanner.core.common.catching
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.usecase.DeleteDocument
import io.github.maborokun.docscanner.domain.usecase.ImportDocument
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val query: String = "",
    /** Documents matching [query] and [category]. */
    val documents: List<Document> = emptyList(),
    /** The category filter; null shows every category. */
    val category: DocumentCategory? = null,
    /** A category the typed [query] names, offered as a one-tap filter; null when none matches or it is applied. */
    val suggestion: DocumentCategory? = null,
    /** Edit mode: rows can be ticked for sharing or deleting. */
    val selecting: Boolean = false,
    val selected: Set<Long> = emptySet(),
    val message: Notice? = null,
) {
    /** True when the library has nothing at all, as opposed to nothing matching a search or filter. */
    val isEmpty: Boolean get() = documents.isEmpty() && query.isBlank() && category == null
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
/** A one-off message for the snackbar: a string resource that takes [detail] (the cause, or null if unknown). */
data class Notice(
    @StringRes val text: Int,
    val detail: String?,
)

class LibraryViewModel(
    observeDocuments: ObserveDocuments,
    private val importDocument: ImportDocument,
    private val deleteDocument: DeleteDocument,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<DocumentCategory?>(null)
    private val selecting = MutableStateFlow(false)
    private val selected = MutableStateFlow<Set<Long>>(emptySet())
    private val message = MutableStateFlow<Notice?>(null)

    private val documents =
        query
            .debounce { if (it.isEmpty()) 0L else SEARCH_DEBOUNCE_MS }
            .flatMapLatest { text ->
                val named = DocumentCategory.matching(text)
                if (named.isEmpty()) {
                    observeDocuments(text)
                } else {
                    // Typing a category's name also lists that category's documents, not only text matches.
                    combine(observeDocuments(text), observeDocuments()) { hits, all ->
                        (
                            hits +
                                all.filter {
                                    it.category in named
                                }
                        ).distinctBy { it.id }.sortedByDescending { it.createdAt }
                    }
                }
            }.combine(category) { all, wanted -> if (wanted == null) all else all.filter { it.category == wanted } }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            combine(query, category, ::Pair),
            documents,
            combine(selecting, selected, ::Pair),
            message,
        ) { (text, wanted), shown, (editing, ticked), note ->
            val suggestion = DocumentCategory.matching(text).firstOrNull { it != wanted }
            LibraryUiState(text, shown, wanted, suggestion, editing, ticked, note)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onQueryChange(value: String) = query.update { value }

    fun onCategoryChange(value: DocumentCategory?) = category.update { value }

    /** Applies the category the query names as a filter and clears the query, which has done its job. */
    fun applySuggestion(value: DocumentCategory) {
        category.update { value }
        query.update { "" }
    }

    fun onMessageShown() = message.update { null }

    /** Enters edit mode with [id] ticked (long press). */
    fun startSelecting(id: Long) {
        selecting.update { true }
        selected.update { setOf(id) }
    }

    fun toggleSelected(id: Long) = selected.update { if (id in it) it - id else it + id }

    /** Leaves edit mode and forgets the ticks. */
    fun cancelSelecting() {
        selecting.update { false }
        selected.update { emptySet() }
    }

    /** [source] is a content URI string from the photo picker. */
    fun import(source: String) {
        viewModelScope.launch {
            catching { importDocument(source, title = "") }
                .onFailure { error ->
                    message.update { Notice(R.string.error_import, error.message) }
                }
        }
    }

    fun delete(id: Long) = deleteAll(listOf(id))

    /** Deletes every ticked document and leaves edit mode. */
    fun deleteSelected() {
        deleteAll(selected.value.toList())
        cancelSelecting()
    }

    private fun deleteAll(ids: List<Long>) {
        viewModelScope.launch {
            ids.forEach { id ->
                catching { deleteDocument(id) }
                    .onFailure { error ->
                        message.update { Notice(R.string.error_delete, error.message) }
                    }
            }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 200L
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

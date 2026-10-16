package io.github.maborokun.docscanner.feature.library

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.FakePageImageStore
import io.github.maborokun.docscanner.domain.fakeImportDocument
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.receipt.FieldError
import io.github.maborokun.docscanner.domain.receipt.FieldsInput
import io.github.maborokun.docscanner.domain.usecase.DeleteDocument
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import io.github.maborokun.docscanner.domain.usecase.RemovePage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        viewModel =
            LibraryViewModel(
                ObserveDocuments(repository),
                fakeImportDocument(repository, store) { NOW },
                DeleteDocument(repository, store),
            )
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun importAddsDocumentTitledWithDate() =
        runTest(dispatcher) {
            viewModel.uiState.test {
                assertEquals(emptyList<Any>(), awaitItem().documents)
                viewModel.import("content://media/r.png")
                val state = awaitUntil { it.documents.isNotEmpty() }
                assertEquals(listOf("Scan 2026-10-15"), state.documents.map { it.title })
            }
        }

    @Test
    fun searchIsDebouncedAndFilters() =
        runTest(dispatcher) {
            repository.insert("Groceries", 1L, null, emptyList())
            repository.insert("Taxes", 2L, null, emptyList())
            viewModel.uiState.test {
                awaitItem()
                advanceUntilIdle()
                assertEquals(listOf("Taxes", "Groceries"), expectMostRecentItem().documents.map { it.title })

                viewModel.onQueryChange("gro")
                advanceTimeBy(100)
                assertEquals(2, expectMostRecentItem().documents.size) // typing has not reached the repository yet
                advanceTimeBy(150)
                assertEquals(listOf("Groceries"), expectMostRecentItem().documents.map { it.title })
            }
        }

    @Test
    fun failedImportShowsMessageThatCanBeDismissed() =
        runTest(dispatcher) {
            store.failImport = true
            viewModel.uiState.test {
                awaitItem()
                viewModel.import("content://media/r.png")
                val message = awaitUntil { it.message != null }.message
                assertEquals(R.string.error_import, message!!.text)
                viewModel.onMessageShown()
                assertNull(awaitUntil { it.message == null }.message)
            }
        }

    @Test
    fun deleteRemovesDocumentAndFiles() =
        runTest(dispatcher) {
            val id = fakeImportDocument(repository, store) { NOW }("content://media/r.png", "x")
            viewModel.delete(id)
            advanceUntilIdle()
            assertNull(repository.observe(id).first())
            assertEquals(listOf("/private/r.png"), store.deleted)
        }

    @Test
    fun detailEmitsContentThenNotFoundAfterDelete() =
        runTest(dispatcher) {
            val id = fakeImportDocument(repository, store) { NOW }("content://media/r.png", "x")
            val detail =
                DetailViewModel(
                    id,
                    repository,
                    repository,
                    DeleteDocument(repository, store),
                    RemovePage(repository, store),
                )
            detail.uiState.test {
                assertEquals(DetailUiState.Loading, awaitItem())
                assertTrue(awaitItem() is DetailUiState.Content)
                var done = false
                detail.delete { done = true }
                assertEquals(DetailUiState.NotFound, awaitItem())
                advanceUntilIdle()
                assertTrue(done)
            }
        }

    @Test
    fun savingFieldsStoresValidInputAndReportsInvalidInput() =
        runTest(dispatcher) {
            val id = fakeImportDocument(repository, store) { NOW }("content://media/r.png", "x")
            val detail =
                DetailViewModel(
                    id,
                    repository,
                    repository,
                    DeleteDocument(repository, store),
                    RemovePage(repository, store),
                )

            detail.saveFields(FieldsInput("Shop", "2026-10-15", "12,50", "eur"))
            advanceUntilIdle()
            val stored =
                repository
                    .observe(id)
                    .first()
                    ?.document
                    ?.extractedFields
            assertEquals(1250L, stored?.totalMinor)
            assertEquals("EUR", stored?.currency)
            assertTrue(detail.formState.value.saved)

            detail.onFieldsEdited()
            assertEquals(FieldsFormState(), detail.formState.value)

            detail.saveFields(FieldsInput(date = "nope", total = "x"))
            assertEquals(setOf(FieldError.Date, FieldError.Total), detail.formState.value.errors)
            assertEquals(
                1250L,
                repository
                    .observe(id)
                    .first()
                    ?.document
                    ?.extractedFields
                    ?.totalMinor,
            )
        }

    @Test
    fun categoryFilterNarrowsTheListAndSuggestionsFollowTheTypedText() =
        runTest(dispatcher) {
            repository.insert("Lunch", 1L, null, emptyList(), DocumentCategory.Receipt)
            repository.insert("ID card", 2L, null, emptyList(), DocumentCategory.Passport)
            viewModel.uiState.test {
                awaitItem()
                advanceUntilIdle()
                assertEquals(2, expectMostRecentItem().documents.size)

                viewModel.onCategoryChange(DocumentCategory.Passport)
                advanceUntilIdle()
                assertEquals(listOf("ID card"), expectMostRecentItem().documents.map { it.title })

                viewModel.onCategoryChange(null)
                viewModel.onQueryChange("rec")
                advanceUntilIdle()
                assertEquals(DocumentCategory.Receipt, expectMostRecentItem().suggestion)

                viewModel.applySuggestion(DocumentCategory.Receipt)
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertEquals("", state.query)
                assertEquals(DocumentCategory.Receipt, state.category)
                assertNull(state.suggestion) // already applied
                assertEquals(listOf("Lunch"), state.documents.map { it.title })
            }
        }

    @Test
    fun longPressStartsEditModeAndCancelLeavesIt() =
        runTest(dispatcher) {
            val first = repository.insert("A", 1L, null, emptyList())
            val second = repository.insert("B", 2L, null, emptyList())
            viewModel.uiState.test {
                awaitItem()
                viewModel.startSelecting(first)
                advanceUntilIdle()
                assertEquals(true to setOf(first), expectMostRecentItem().let { it.selecting to it.selected })

                viewModel.toggleSelected(second)
                viewModel.toggleSelected(first)
                advanceUntilIdle()
                assertEquals(setOf(second), expectMostRecentItem().selected)

                viewModel.cancelSelecting()
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertEquals(false, state.selecting)
                assertTrue(state.selected.isEmpty())
            }
        }

    @Test
    fun deletingTheSelectionRemovesThoseDocumentsAndLeavesEditMode() =
        runTest(dispatcher) {
            val keep = repository.insert("Keep", 1L, null, emptyList())
            val drop1 = repository.insert("Drop 1", 2L, null, emptyList())
            val drop2 = repository.insert("Drop 2", 3L, null, emptyList())
            viewModel.uiState.test {
                awaitItem()
                viewModel.startSelecting(drop1)
                viewModel.toggleSelected(drop2)
                advanceUntilIdle()
                expectMostRecentItem()

                viewModel.deleteSelected()
                advanceUntilIdle()
                val state = expectMostRecentItem()
                assertEquals(listOf(keep), state.documents.map { it.id })
                assertEquals(false, state.selecting)
            }
        }

    @Test
    fun detailSavesCategoryAndComment() =
        runTest(dispatcher) {
            val id = repository.insert("Doc", 1L, null, emptyList())
            val detail =
                DetailViewModel(
                    id,
                    repository,
                    repository,
                    DeleteDocument(repository, store),
                    RemovePage(repository, store),
                )
            detail.setCategory(DocumentCategory.BookPage)
            detail.setComment("chapter 3")
            advanceUntilIdle()
            val document = repository.observe(id).first()!!.document
            assertEquals(DocumentCategory.BookPage, document.category)
            assertEquals("chapter 3", document.comment)
        }

    private companion object {
        // 2026-10-15T12:00:00Z
        const val NOW = 1_792_065_600_000L
    }
}

private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean): T {
    while (true) {
        val item = awaitItem()
        if (predicate(item)) return item
    }
}

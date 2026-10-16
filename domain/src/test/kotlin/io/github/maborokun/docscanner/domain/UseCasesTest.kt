package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.usecase.DeleteDocument
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UseCasesTest {
    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()
    private var now = 1_000L
    private val import = fakeImportDocument(repository, store) { now++ }
    private val observe = ObserveDocuments(repository)
    private val delete = DeleteDocument(repository, store)

    @Test
    fun importCreatesSinglePageDocument() =
        runTest {
            val id = import("content://media/receipt-1.png", "  Cafe receipt ")

            val doc = repository.observe(id).first()
            assertNotNull(doc)
            assertEquals("Cafe receipt", doc.document.title)
            assertEquals(1, doc.document.pageCount)
            assertEquals("/private/receipt-1.png", doc.pages.single().imagePath)
        }

    @Test
    fun blankTitleFallsBackToScanDate() =
        runTest {
            val id = import("content://media/a.png", "   ")
            assertEquals(
                "Scan 1970-01-01",
                repository
                    .observe(id)
                    .first()
                    ?.document
                    ?.title,
            )
        }

    @Test
    fun failedInsertRemovesCopiedImage() =
        runTest {
            repository.failNextInsert = true
            assertFailsWith<IllegalStateException> { import("content://media/a.png", "x") }
            assertEquals(listOf("/private/a.png"), store.deleted)
        }

    @Test
    fun failedImportInsertsNothing() =
        runTest {
            store.failImport = true
            assertFailsWith<IllegalStateException> { import("content://media/a.png", "x") }
            assertTrue(observe().first().isEmpty())
        }

    @Test
    fun observeListsNewestFirstAndBlankQueryReturnsAll() =
        runTest {
            import("content://media/a.png", "old")
            import("content://media/b.png", "new")
            assertEquals(listOf("new", "old"), observe("  ").first().map { it.title })
        }

    @Test
    fun searchMatchesTitleMerchantAndOcrText() =
        runTest {
            import("content://media/a.png", "Groceries")
            val id =
                repository.insert(
                    title = "Scan 2",
                    createdAt = 5_000L,
                    fields = ReceiptFields(merchant = "Blue Bakery"),
                    pages = listOf(NewPage("p", 1, 1, ocrText = "Croissant 3.20")),
                )
            assertEquals(listOf("Groceries"), observe("groc").first().map { it.title })
            assertEquals(listOf(id), observe("bakery").first().map { it.id })
            assertEquals(listOf(id), observe(" croissant ").first().map { it.id })
            assertTrue(observe("nothing").first().isEmpty())
        }

    @Test
    fun deleteRemovesDocumentAndItsImages() =
        runTest {
            val id = import("content://media/a.png", "x")
            delete(id)
            assertTrue(observe().first().isEmpty())
            assertEquals(listOf("/private/a.png"), store.deleted)
        }

    @Test
    fun updateFieldsStoresReceiptValues() =
        runTest {
            val id = import("content://media/a.png", "x")
            val fields = ReceiptFields(merchant = "Shop", totalMinor = 1250, currency = "EUR", confidence = 0.8f)
            repository.updateFields(id, fields)
            assertEquals(
                fields,
                repository
                    .observe(id)
                    .first()
                    ?.document
                    ?.extractedFields,
            )
        }
}

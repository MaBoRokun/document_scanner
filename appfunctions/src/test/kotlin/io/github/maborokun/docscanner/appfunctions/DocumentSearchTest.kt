package io.github.maborokun.docscanner.appfunctions

import io.github.maborokun.docscanner.domain.FakeDocumentRepository
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class DocumentSearchTest {
    private val repository = FakeDocumentRepository()
    private val search = DocumentSearch(ObserveDocuments(repository), ZoneOffset.UTC)
    private val day = 86_400_000L

    private suspend fun add(
        title: String,
        daysAfterEpoch: Long,
        fields: ReceiptFields? = null,
        text: String = "",
    ) = repository.insert(title, daysAfterEpoch * day, fields, listOf(NewPage("/secret/path.jpg", 10, 10, text)))

    @Test
    fun summariesCarryOnlyTitleDateTotalAndPageCount() =
        runTest {
            add(
                "Blue Bakery",
                20_000,
                ReceiptFields("Blue Bakery", LocalDate.of(2026, 4, 19), 3518, "EUR", 1f),
                "secret ocr words",
            )

            val summary = search.search("bakery").single()
            assertEquals(DocumentSummary("Blue Bakery", "2024-10-04", "35.18", "EUR", 1), summary)
            // The type has no field that could leak images or text.
            assertEquals(
                setOf("title", "scannedOn", "total", "currency", "pageCount"),
                DocumentSummary::class.java.declaredFields
                    .map { it.name }
                    .toSet(),
            )
        }

    @Test
    fun documentsWithoutFieldsHaveNoTotal() =
        runTest {
            add("Notes", 1)
            val summary = search.search("").single()
            assertNull(summary.total)
            assertNull(summary.currency)
        }

    @Test
    fun matchesTextInsideThePagesToo() =
        runTest {
            add("Scan 1", 1, text = "Croissant 3.20")
            add("Scan 2", 2, text = "Bread")
            assertEquals(listOf("Scan 1"), search.search("croissant").map { it.title })
        }

    @Test
    fun blankQueryListsTheNewestFirst() =
        runTest {
            add("old", 1)
            add("new", 5)
            assertEquals(listOf("new", "old"), search.search("   ").map { it.title })
        }

    @Test
    fun resultsAreCapped() =
        runTest {
            repeat(DocumentSearch.MAX_RESULTS + 5) { add("Doc $it", it.toLong()) }
            assertEquals(DocumentSearch.MAX_RESULTS, search.search("").size)
        }

    @Test
    fun unknownQueryFindsNothing() =
        runTest {
            add("Something", 1)
            assertTrue(search.search("zzz").isEmpty())
        }
}

package io.github.maborokun.docscanner.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.PageTint
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.model.Stroke
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RoomDocumentRepositoryTest {
    private lateinit var repository: RoomDocumentRepository

    @After
    fun tearDown() = repository.close()

    @Before
    fun setUp() {
        repository = RoomDocumentRepository.inMemory(ApplicationProvider.getApplicationContext<Context>())
    }

    private fun page(
        path: String,
        text: String = "",
    ) = NewPage(path, 100, 200, text)

    @Test
    fun insertThenObserveReturnsPagesInOrder() =
        runBlocking {
            val quad = Quad(Point(0.1f, 0.1f), Point(0.9f, 0.1f), Point(0.9f, 0.9f), Point(0.1f, 0.9f))
            val id = repository.insert("Receipt", 10L, null, listOf(page("a.img"), page("b.img").copy(quad = quad)))

            val doc = repository.observe(id).first()
            assertNotNull(doc)
            assertEquals(2, doc!!.document.pageCount)
            assertEquals(listOf("a.img", "b.img"), doc.pages.map { it.imagePath })
            assertEquals(quad, doc.pages[1].quad)
            assertNull(doc.document.extractedFields)
        }

    @Test
    fun listIsNewestFirstWithPageCounts() =
        runBlocking {
            repository.insert("old", 1L, null, listOf(page("1")))
            repository.insert("new", 2L, null, listOf(page("2"), page("3")))

            val list = repository.observeAll().first()
            assertEquals(listOf("new", "old"), list.map { it.title })
            assertEquals(listOf(2, 1), list.map { it.pageCount })
            assertEquals(listOf("2", "1"), list.map { it.thumbnailPath })
        }

    @Test
    fun searchFindsByOcrTextTitleAndMerchant() =
        runBlocking {
            val bakery = repository.insert("Scan 1", 1L, null, listOf(page("1", "Croissant 3.20 TOTAL 3.20")))
            val groceries =
                repository.insert(
                    "Groceries",
                    2L,
                    ReceiptFields(merchant = "Fresh Market"),
                    listOf(page("2")),
                )

            assertEquals(listOf(bakery), repository.search("croiss").first().map { it.id })
            assertEquals(listOf(groceries), repository.search("grocer").first().map { it.id })
            assertEquals(listOf(groceries), repository.search("market").first().map { it.id })
            assertEquals(listOf(groceries, bakery), repository.search("   ").first().map { it.id })
            assertTrue(repository.search("zzz").first().isEmpty())
        }

    @Test
    fun searchSurvivesHostileInput() =
        runBlocking {
            repository.insert("Receipt 100%", 1L, null, listOf(page("1", "plain text")))
            val inputs = listOf("\"", "AND OR", "-", "*", "(", "a\"b", "%", "_", "\\", "NEAR/2")
            inputs.forEach { repository.search(it).first() }
            assertEquals(1, repository.search("100%").first().size)
            assertTrue(repository.search("%zzz").first().isEmpty())
        }

    @Test
    fun deletedDocumentDisappearsFromSearch() =
        runBlocking {
            val keep = repository.insert("keep", 1L, null, listOf(page("1", "alpha beta")))
            val drop = repository.insert("drop", 2L, null, listOf(page("2", "alpha gamma")))
            repository.delete(drop)
            assertEquals(listOf(keep), repository.search("alpha").first().map { it.id })
            assertTrue(repository.search("gamma").first().isEmpty())
        }

    @Test
    fun deleteReturnsImagePathsAndCascadesPages() =
        runBlocking {
            val id = repository.insert("x", 1L, null, listOf(page("a.img"), page("b.img")))
            assertEquals(listOf("a.img", "b.img"), repository.delete(id))
            assertNull(repository.observe(id).first())
            assertTrue(repository.observeAll().first().isEmpty())
            assertTrue(repository.delete(id).isEmpty())
        }

    @Test
    fun receiptFieldsRoundTripAndCanBeUpdated() =
        runBlocking {
            val fields = ReceiptFields("Shop", LocalDate.of(2026, 10, 15), 1250L, "EUR", 0.9f)
            val id = repository.insert("r", 1L, fields, listOf(page("1")))
            assertEquals(
                fields,
                repository
                    .observe(id)
                    .first()!!
                    .document.extractedFields,
            )

            val edited = fields.copy(totalMinor = 999L, merchant = "Other")
            repository.updateFields(id, edited)
            assertEquals(
                edited,
                repository
                    .observeAll()
                    .first()
                    .single()
                    .extractedFields,
            )

            repository.updateFields(id, null)
            assertNull(
                repository
                    .observe(id)
                    .first()!!
                    .document.extractedFields,
            )
        }

    @Test
    fun addedPagesFollowTheExistingOnesAndAreSearchable() =
        runBlocking {
            val id = repository.insert("Trip", 1L, null, listOf(page("a.img", "first page")))
            repository.addPage(id, page("b.img", "second page about croissants"))
            repository.addPage(id, page("c.img"))

            val doc = repository.observe(id).first()!!
            assertEquals(listOf("a.img", "b.img", "c.img"), doc.pages.map { it.imagePath })
            assertEquals(listOf(0, 1, 2), doc.pages.map { it.index })
            assertEquals(
                3,
                repository
                    .observeAll()
                    .first()
                    .single()
                    .pageCount,
            )
            assertEquals(listOf(id), repository.search("croissants").first().map { it.id })
        }

    @Test
    fun addingToAMissingDocumentFails() =
        runBlocking {
            val failed = runCatching { repository.addPage(404L, page("x.img")) }
            assertTrue(failed.isFailure)
        }

    @Test
    fun removingAPageKeepsTheRestAndNeverTheLastOne() =
        runBlocking {
            val id = repository.insert("Trip", 1L, null, listOf(page("a.img", "alpha"), page("b.img", "bravo")))
            val pages = repository.observe(id).first()!!.pages

            assertEquals(listOf("b.img"), repository.removePage(pages[1].id))
            assertEquals(
                listOf("a.img"),
                repository
                    .observe(id)
                    .first()!!
                    .pages
                    .map { it.imagePath },
            )
            assertTrue(repository.search("bravo").first().isEmpty())

            assertTrue(repository.removePage(pages[0].id).isEmpty()) // last page stays
            assertTrue(repository.removePage(99_999L).isEmpty()) // unknown page
            assertEquals(
                1,
                repository
                    .observe(id)
                    .first()!!
                    .pages.size,
            )
        }

    @Test
    fun categoryAndCommentAreStoredEditedAndSearchable() =
        runBlocking {
            val id = repository.insert("Scan", 1L, null, listOf(page("a.img")), DocumentCategory.Passport)
            assertEquals(
                DocumentCategory.Passport,
                repository
                    .observe(id)
                    .first()!!
                    .document.category,
            )
            assertEquals(
                DocumentCategory.Passport,
                repository
                    .observeAll()
                    .first()
                    .single()
                    .category,
            )

            repository.updateCategory(id, DocumentCategory.Receipt)
            repository.updateComment(id, "for the accountant")

            val document = repository.observe(id).first()!!.document
            assertEquals(DocumentCategory.Receipt, document.category)
            assertEquals("for the accountant", document.comment)
            assertEquals(listOf(id), repository.search("accountant").first().map { it.id })
        }

    @Test
    fun originalAndEditsSurviveAndReplacingAPageSwapsThemAndReturnsTheOldImage() =
        runBlocking {
            val edit =
                PageEdit(
                    EnhanceMode.BlackWhite,
                    -0.25f,
                    PageTint.Cool,
                    listOf(Stroke(-65536, 0.01f, listOf(Point(0.1f, 0.2f), Point(0.3f, 0.4f)))),
                )
            val id =
                repository.insert(
                    "Doc",
                    1L,
                    null,
                    listOf(page("flat.img", "one").copy(originalPath = "orig.img", edit = edit)),
                )
            val stored =
                repository
                    .observe(id)
                    .first()!!
                    .pages
                    .single()
            assertEquals("orig.img", stored.originalPath)
            assertEquals(edit, stored.edit)

            val old = repository.replacePage(stored.id, NewPage("new.img", 50, 60, "two", originalPath = "orig.img"))

            assertEquals("flat.img", old)
            val updated =
                repository
                    .observe(id)
                    .first()!!
                    .pages
                    .single()
            assertEquals(
                listOf("new.img", 50, 60, "two", PageEdit()),
                listOf(updated.imagePath, updated.width, updated.height, updated.ocrText, updated.edit),
            )
            assertTrue(repository.search("one").first().isEmpty()) // the text index follows the new text
            assertEquals(listOf(id), repository.search("two").first().map { it.id })
            assertNull(repository.replacePage(99_999L, page("x.img")))
        }

    @Test
    fun deletingReturnsEveryFileIncludingOriginals() =
        runBlocking {
            val id =
                repository.insert(
                    "Doc",
                    1L,
                    null,
                    listOf(page("a.img").copy(originalPath = "a-orig.img"), page("b.img").copy(originalPath = "b.img")),
                )
            assertEquals(setOf("a.img", "a-orig.img", "b.img"), repository.delete(id).toSet())
        }
}

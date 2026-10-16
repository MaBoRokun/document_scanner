package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.StoredImage
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.usecase.ImportDocument
import io.github.maborokun.docscanner.domain.usecase.RemovePage
import io.github.maborokun.docscanner.domain.usecase.ScanDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScanDocumentTest {
    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()
    private val processor = FakePageProcessor()
    private val recognizer = FakeTextRecognizer("Blue Bakery\n2026-04-19\nTotal EUR 35.18")
    private val scan =
        ScanDocument(processor, recognizer, repository, repository, store, ZoneOffset.UTC) { 86_400_000L }
    private val staged = StoredImage("/private/staged.jpg", 3000, 4000)
    private val quad = Quad(Point(0.1f, 0.1f), Point(0.9f, 0.15f), Point(0.85f, 0.9f), Point(0.12f, 0.88f))

    @Test
    fun fullImageWithoutEnhancementSkipsProcessingAndKeepsTheStagedFile() =
        runTest {
            val id = scan(staged)

            assertTrue(processor.warps.isEmpty())
            assertTrue(processor.enhances.isEmpty())
            assertEquals(listOf("/private/staged.jpg"), recognizer.recognized)
            val doc = requireNotNull(repository.observe(id).first())
            assertEquals("/private/staged.jpg", doc.pages.single().imagePath)
            assertTrue(store.deleted.isEmpty())
        }

    @Test
    fun warpThenEnhanceThenSaveKeepsOnlyTheFinalImage() =
        runTest {
            val id = scan(staged, quad, EnhanceMode.Contrast)

            val flat = "/private/alloc-1.jpg"
            val enhanced = "/private/alloc-2.jpg"
            assertEquals(Triple("/private/staged.jpg", quad, flat), processor.warps.single())
            assertEquals(Triple(flat, EnhanceMode.Contrast, enhanced), processor.enhances.single())
            assertEquals(listOf(flat), recognizer.recognized) // OCR sees the un-enhanced flat page

            val page = requireNotNull(repository.observe(id).first()).pages.single()
            assertEquals(enhanced, page.imagePath)
            assertEquals(600, page.width)
            assertEquals(quad, page.quad)
            assertEquals(setOf(flat), store.deleted.toSet()) // the staged photo stays as the page's original
            assertEquals("/private/staged.jpg", page.originalPath)
            assertEquals(EnhanceMode.Contrast, page.edit.mode)
        }

    @Test
    fun aReceiptIsFiledAsAReceiptAndAnOtherwiseUnknownScanAsADocument() =
        runTest {
            val receipt = requireNotNull(repository.observe(scan(staged)).first())
            assertEquals(
                io.github.maborokun.docscanner.domain.model.DocumentCategory.Receipt,
                receipt.document.category,
            )

            recognizer.text = "..."
            val other = requireNotNull(repository.observe(scan(staged.copy(width = 100, height = 100))).first())
            assertEquals(io.github.maborokun.docscanner.domain.model.DocumentCategory.Document, other.document.category)
        }

    @Test
    fun parsedFieldsAndTextAreStoredAndTitleUsesTheMerchant() =
        runTest {
            val id = scan(staged)
            val doc = requireNotNull(repository.observe(id).first())

            assertEquals("Blue Bakery", doc.document.title)
            assertEquals(LocalDate.of(2026, 4, 19), doc.document.extractedFields?.date)
            assertEquals(3518L, doc.document.extractedFields?.totalMinor)
            assertTrue("Total EUR 35.18" in doc.pages.single().ocrText)
        }

    @Test
    fun explicitTitleWinsAndNoFieldsMeansNoFields() =
        runTest {
            recognizer.text = "..."
            val id = scan(staged, title = "  My scan ")
            val doc = requireNotNull(repository.observe(id).first())
            assertEquals("My scan", doc.document.title)
            assertNull(doc.document.extractedFields)
        }

    @Test
    fun blankTextFallsBackToScanDate() =
        runTest {
            recognizer.text = ""
            val id = scan(staged)
            assertEquals("Scan 1970-01-02", requireNotNull(repository.observe(id).first()).document.title)
        }

    @Test
    fun ocrFailureStillSavesThePage() =
        runTest {
            recognizer.fail = true
            val id = scan(staged, quad, EnhanceMode.None)
            val page = requireNotNull(repository.observe(id).first()).pages.single()
            assertEquals("", page.ocrText)
            assertEquals("/private/alloc-1.jpg", page.imagePath)
        }

    @Test
    fun enhanceFailureFallsBackToTheFlatPage() =
        runTest {
            processor.failEnhance = true
            val id = scan(staged, quad, EnhanceMode.BlackWhite)
            val page = requireNotNull(repository.observe(id).first()).pages.single()
            assertEquals("/private/alloc-1.jpg", page.imagePath)
            assertTrue("/private/alloc-2.jpg" in store.deleted)
        }

    @Test
    fun failedInsertCleansUpEverything() =
        runTest {
            repository.failNextInsert = true
            assertFailsWith<IllegalStateException> { scan(staged, quad, EnhanceMode.Contrast) }
            assertEquals(
                setOf("/private/staged.jpg", "/private/alloc-1.jpg", "/private/alloc-2.jpg"),
                store.deleted.toSet(),
            )
        }

    @Test
    fun cancellationDuringProcessingCleansUp() =
        runTest {
            val failing =
                object : io.github.maborokun.docscanner.domain.processing.PageProcessor {
                    override suspend fun warp(
                        input: String,
                        quad: Quad,
                        output: String,
                    ): io.github.maborokun.docscanner.domain.model.FrameSize = throw CancellationException("cancelled")

                    override suspend fun enhance(
                        input: String,
                        mode: EnhanceMode,
                        output: String,
                    ) = Unit
                }
            val cancelled = ScanDocument(failing, recognizer, repository, repository, store, ZoneOffset.UTC) { 0L }
            assertFailsWith<CancellationException> { cancelled(staged, quad) }
            assertTrue("/private/staged.jpg" in store.deleted)
        }

    @Test
    fun importStagesTheSourceThenScansIt() =
        runTest {
            val id = ImportDocument(store, scan)("content://media/receipt.png", "")
            val doc = requireNotNull(repository.observe(id).first())
            assertNotNull(doc.pages.single().imagePath)
            assertEquals("/private/receipt.png", doc.pages.single().imagePath)
        }

    @Test
    fun appendingAddsAPageAndKeepsTheDocumentsTitleAndFields() =
        runTest {
            val id = scan(staged, title = "Trip")
            val before = requireNotNull(repository.observe(id).first())

            recognizer.text = "Other Shop\n2026-12-12\nTotal 99.00"
            val again = scan(StoredImage("/private/second.jpg", 10, 10), appendTo = id)

            assertEquals(id, again)
            val after = requireNotNull(repository.observe(id).first())
            assertEquals(2, after.pages.size)
            assertEquals("Trip", after.document.title)
            assertEquals(before.document.extractedFields, after.document.extractedFields)
            assertTrue("Other Shop" in after.pages[1].ocrText)
        }

    @Test
    fun appendingToAMissingDocumentFailsAndCleansUp() =
        runTest {
            assertFailsWith<IllegalStateException> { scan(staged, appendTo = 404L) }
            assertTrue("/private/staged.jpg" in store.deleted)
        }

    @Test
    fun removePageDeletesItsFileButNeverTheLastPage() =
        runTest {
            val id = scan(staged)
            scan(StoredImage("/private/second.jpg", 10, 10), appendTo = id)
            val pages = requireNotNull(repository.observe(id).first()).pages
            val remove = RemovePage(repository, store)

            remove(pages[1].id)
            assertEquals(1, requireNotNull(repository.observe(id).first()).pages.size)
            assertTrue("/private/second.jpg" in store.deleted)

            store.deleted.clear()
            remove(pages[0].id) // the only page left
            assertEquals(1, requireNotNull(repository.observe(id).first()).pages.size)
            assertTrue(store.deleted.isEmpty())

            remove(12345L) // unknown page
            assertTrue(store.deleted.isEmpty())
        }
}

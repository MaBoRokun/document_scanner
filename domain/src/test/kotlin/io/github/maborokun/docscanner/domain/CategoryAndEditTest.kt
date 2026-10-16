package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.classify.CategoryRule
import io.github.maborokun.docscanner.domain.classify.DocumentClassifier
import io.github.maborokun.docscanner.domain.classify.pageAspect
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.PageTint
import io.github.maborokun.docscanner.domain.model.Point
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.model.Stroke
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.usecase.EditPage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentClassifierTest {
    private val classifier = DocumentClassifier()
    private val longText = "word ".repeat(120)

    @Test
    fun aConfidentReceiptWinsOverEverythingElse() {
        val fields = ReceiptFields("Blue Bakery", null, 3518, "EUR", confidence = 0.8f)
        assertEquals(DocumentCategory.Receipt, classifier.classify(1.414f, longText, fields))
    }

    @Test
    fun passportsAreRecognisedByWordsAndByTheMachineReadableZone() {
        assertEquals(DocumentCategory.Passport, classifier.classify(1.4f, "REPUBLIC PASSPORT No 123", null))
        assertEquals(
            DocumentCategory.Passport,
            classifier.classify(1.4f, "P<UTOERIKSSON<<ANNA<MARIA<<<<", null),
        )
    }

    @Test
    fun a4AndLetterProportionsMeanAnA4Page() {
        assertEquals(DocumentCategory.A4Page, classifier.classify(1.41f, "", null))
        assertEquals(DocumentCategory.A4Page, classifier.classify(1.30f, "", null))
    }

    @Test
    fun aDensePageThatIsNotA4IsABookPage() {
        assertEquals(DocumentCategory.BookPage, classifier.classify(1.55f, longText, null))
        assertEquals(DocumentCategory.Document, classifier.classify(1.55f, "short", null))
    }

    @Test
    fun anythingElseIsAGenericDocument() {
        assertEquals(DocumentCategory.Document, classifier.classify(1.0f, "", null))
    }

    @Test
    fun aspectIsLongOverShortWhateverTheRotation() {
        assertEquals(2f, pageAspect(100, 200))
        assertEquals(2f, pageAspect(200, 100))
        assertEquals(1f, pageAspect(0, 0))
    }
}

class DocumentCategoryTest {
    @Test
    fun typedTextFindsCategoriesByLabel() {
        assertEquals(listOf(DocumentCategory.Receipt), DocumentCategory.matching("rec"))
        assertEquals(listOf(DocumentCategory.Passport), DocumentCategory.matching("Pass"))
        assertEquals(listOf(DocumentCategory.BookPage, DocumentCategory.A4Page), DocumentCategory.matching("page"))
    }

    @Test
    fun tooShortOrUnrelatedTextMatchesNothing() {
        assertTrue(DocumentCategory.matching("r").isEmpty())
        assertTrue(DocumentCategory.matching("   ").isEmpty())
        assertTrue(DocumentCategory.matching("zzz").isEmpty())
    }

    @Test
    fun unknownStoredNamesFallBackToDocument() {
        assertEquals(DocumentCategory.Receipt, DocumentCategory.fromName("Receipt"))
        assertEquals(DocumentCategory.Document, DocumentCategory.fromName("FromTheFuture"))
        assertEquals(DocumentCategory.Document, DocumentCategory.fromName(null))
    }
}

class PageEditTest {
    @Test
    fun anUntouchedEditHasTheIdentityMatrixAndNoColorChange() {
        val edit = PageEdit()
        assertTrue(!edit.hasColorChange)
        assertContentEquals(PageTint.None.matrix, edit.colorMatrix())
    }

    @Test
    fun brightnessShiftsTheRgbOffsetsOnly() {
        val matrix = PageEdit(brightness = 0.5f).colorMatrix()
        assertEquals(127.5f, matrix[4])
        assertEquals(127.5f, matrix[9])
        assertEquals(127.5f, matrix[14])
        assertEquals(0f, matrix[19]) // alpha is untouched
    }

    @Test
    fun theMatrixIsACopyNotTheSharedTintArray() {
        PageEdit(brightness = 1f, tint = PageTint.Warm).colorMatrix()
        assertEquals(0f, PageTint.Warm.matrix[4])
    }
}

class EditPageTest {
    private val repository = FakeDocumentRepository()
    private val store = FakePageImageStore()
    private val renderer = FakePageRenderer()
    private val recognizer = FakeTextRecognizer("fresh text")
    private val editPage = EditPage(renderer, recognizer, repository, store)
    private val quad = Quad(Point(0.1f, 0.1f), Point(0.9f, 0.1f), Point(0.9f, 0.9f), Point(0.1f, 0.9f))

    private suspend fun storedPage(original: String? = "/p/original.jpg") =
        repository
            .insert("Doc", 1L, null, listOf(NewPage("/p/old.jpg", 10, 10, "old text", originalPath = original)))
            .let {
                repository
                    .observe(it)
                    .first()!!
                    .pages
                    .single()
            }

    @Test
    fun rendersFromTheOriginalSwapsThePageAndDropsTheOldImage() =
        runTest {
            val page = storedPage()
            val edit =
                PageEdit(
                    EnhanceMode.Contrast,
                    0.2f,
                    PageTint.Sepia,
                    listOf(Stroke(0xFF000000.toInt(), 0.01f, listOf(Point(0.5f, 0.5f)))),
                )

            val output = editPage(page, quad, edit)

            assertEquals(Triple("/p/original.jpg", quad, edit), renderer.renders.single())
            val updated =
                repository
                    .observe(page.documentId)
                    .first()!!
                    .pages
                    .single()
            assertEquals(output, updated.imagePath)
            assertEquals("fresh text", updated.ocrText) // read again from the edited image
            assertEquals(quad, updated.quad)
            assertEquals(edit, updated.edit)
            assertEquals("/p/original.jpg", updated.originalPath)
            assertEquals(listOf("/p/old.jpg"), store.deleted)
        }

    @Test
    fun anImageThatIsItsOwnOriginalIsNeverDeleted() =
        runTest {
            val page = storedPage(original = null) // saved before originals were kept: the flat image is the original
            editPage(page, Quad.FULL, PageEdit(brightness = 0.1f))

            assertEquals("/p/old.jpg", renderer.renders.single().first)
            assertTrue(store.deleted.isEmpty())
            assertEquals(
                "/p/old.jpg",
                repository
                    .observe(page.documentId)
                    .first()!!
                    .pages
                    .single()
                    .originalPath,
            )
        }

    @Test
    fun aFailedRenderLeavesThePageAndCleansUp() =
        runTest {
            val page = storedPage()
            renderer.fail = true

            assertFailsWith<IllegalStateException> { editPage(page, quad, PageEdit()) }

            assertEquals(
                "/p/old.jpg",
                repository
                    .observe(page.documentId)
                    .first()!!
                    .pages
                    .single()
                    .imagePath,
            )
            assertEquals(listOf(store.allocatedPaths.single()), store.deleted)
        }

    @Test
    fun aFailedTextReadKeepsThePreviousText() =
        runTest {
            val page = storedPage()
            recognizer.fail = true

            editPage(page, quad, PageEdit())

            assertEquals(
                "old text",
                repository
                    .observe(page.documentId)
                    .first()!!
                    .pages
                    .single()
                    .ocrText,
            )
        }

    @Test
    fun aVanishedPageIsAnErrorAndTheRenderIsRemoved() =
        runTest {
            val page = storedPage()
            repository.delete(page.documentId)

            assertFailsWith<IllegalStateException> { editPage(page, quad, PageEdit()) }

            assertEquals(listOf(store.allocatedPaths.single()), store.deleted)
            assertNull(repository.observe(page.documentId).first())
        }
}

class ClassifierExtensionTest {
    @Test
    fun aNewRuleIsAddedWithoutTouchingTheExistingOnes() {
        val shopping = CategoryRule { if ("grocery" in it.text) DocumentCategory.Other else null }
        val classifier = DocumentClassifier(listOf(shopping) + DocumentClassifier.DEFAULT_RULES)
        assertEquals(DocumentCategory.Other, classifier.classify(1.41f, "grocery list", null))
        assertEquals(DocumentCategory.A4Page, classifier.classify(1.41f, "something else", null))
    }

    @Test
    fun theFallbackAppliesWhenNoRuleHasAnOpinion() {
        assertEquals(
            DocumentCategory.Other,
            DocumentClassifier(emptyList(), DocumentCategory.Other).classify(1f, "", null),
        )
    }
}

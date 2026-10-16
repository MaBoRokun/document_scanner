package io.github.maborokun.docscanner.domain.usecase

import io.github.maborokun.docscanner.domain.classify.DocumentClassifier
import io.github.maborokun.docscanner.domain.classify.pageAspect
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.StoredImage
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.processing.PageProcessor
import io.github.maborokun.docscanner.domain.processing.TextRecognizer
import io.github.maborokun.docscanner.domain.receipt.ReceiptParser
import io.github.maborokun.docscanner.domain.repository.DocumentWriter
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import io.github.maborokun.docscanner.domain.repository.PageWriter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import kotlin.coroutines.cancellation.CancellationException

/**
 * Turns a staged (already upright, private) image into a stored one-page document:
 * `warp -> (enhance || OCR) -> parse -> persist`. A failed OCR or enhancement only loses that extra, never the page.
 * With [appendTo] the page is added to that document instead, which keeps its title and fields.
 * The staged [StoredImage] stays as the page's original (so the crop can be changed later) once the page is saved;
 * every intermediate file is removed, whether or not the document was saved.
 */
class ScanDocument(
    private val processor: PageProcessor,
    private val recognizer: TextRecognizer,
    private val documents: DocumentWriter,
    private val pages: PageWriter,
    private val store: PageImageStore,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val classifier: DocumentClassifier = DocumentClassifier(),
    private val clock: () -> Long,
) {
    /** Returns the new document id. A blank [title] becomes the merchant, or "Scan <date>" when none was found. */
    suspend operator fun invoke(
        image: StoredImage,
        quad: Quad = Quad.FULL,
        mode: EnhanceMode = EnhanceMode.None,
        title: String = "",
        appendTo: Long? = null,
    ): Long {
        val created = mutableListOf<String>()
        var retained: Set<String> = emptySet()
        try {
            val flat = if (quad == Quad.FULL) image else warp(image, quad, created)
            val (finalPath, text) =
                coroutineScope {
                    val ocr = async { ocrOrEmpty(flat.path) }
                    val enhanced = async { enhanceOrKeep(flat, mode, created) }
                    enhanced.await() to ocr.await()
                }
            val page = NewPage(finalPath, flat.width, flat.height, text, quad, image.path, PageEdit(mode))
            val id = appendTo?.also { pages.addPage(it, page) } ?: insertNew(page, text, title)
            retained = setOf(finalPath, image.path)
            return id
        } finally {
            // Also runs when the caller is cancelled, so nothing is left behind in private storage.
            withContext(NonCancellable) { store.delete((created + image.path).filter { it !in retained }) }
        }
    }

    private suspend fun insertNew(
        page: NewPage,
        text: String,
        title: String,
    ): Long {
        val fields = ReceiptParser.parse(text).takeIf { it.confidence > 0f }
        val now = clock()
        val fallback = fields?.merchant ?: ("Scan " + Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
        val category = classifier.classify(pageAspect(page.width, page.height), text, fields)
        return documents.insert(title.trim().ifEmpty { fallback }, now, fields, listOf(page), category)
    }

    private suspend fun warp(
        image: StoredImage,
        quad: Quad,
        created: MutableList<String>,
    ): StoredImage {
        val out = store.allocatePath()
        created += out
        val size = processor.warp(image.path, quad, out)
        return StoredImage(out, size.width, size.height)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun ocrOrEmpty(path: String): String =
        try {
            recognizer.recognize(path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ""
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun enhanceOrKeep(
        flat: StoredImage,
        mode: EnhanceMode,
        created: MutableList<String>,
    ): String {
        if (mode == EnhanceMode.None) return flat.path
        val out = store.allocatePath()
        created += out
        return try {
            processor.enhance(flat.path, mode, out)
            out
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            flat.path
        }
    }
}

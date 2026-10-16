package io.github.maborokun.docscanner.domain.usecase

import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.processing.PageRenderer
import io.github.maborokun.docscanner.domain.processing.TextRecognizer
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import io.github.maborokun.docscanner.domain.repository.PageWriter
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Re-renders a page from its original photo with a new crop and edits, reads its text again (so anything the user
 * covered with the pen is not in the text) and swaps it into the document. The previous image is removed unless it
 * is the original. Returns the new image path.
 */
class EditPage(
    private val renderer: PageRenderer,
    private val recognizer: TextRecognizer,
    private val repository: PageWriter,
    private val store: PageImageStore,
) {
    suspend operator fun invoke(
        page: Page,
        quad: Quad,
        edit: PageEdit,
    ): String {
        // Pages saved before originals were kept have only the flat image, which then is the original.
        val original = page.originalPath ?: page.imagePath
        val output = store.allocatePath()
        var stored = false
        try {
            val size = renderer.render(original, quad, edit, output)
            val text = ocrOrKeep(output, page.ocrText)
            val old =
                repository.replacePage(
                    page.id,
                    NewPage(output, size.width, size.height, text, quad, original, edit),
                )
            stored = old != null
            if (old != null && old != original) store.delete(listOf(old))
            check(stored) { "The page no longer exists" }
            return output
        } finally {
            if (!stored) withContext(NonCancellable) { store.delete(listOf(output)) }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun ocrOrKeep(
        path: String,
        previous: String,
    ): String =
        try {
            recognizer.recognize(path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            previous
        }
}

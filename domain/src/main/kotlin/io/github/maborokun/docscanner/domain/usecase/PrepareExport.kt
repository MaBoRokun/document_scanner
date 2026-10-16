package io.github.maborokun.docscanner.domain.usecase

import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.repository.DocumentReader
import kotlinx.coroutines.flow.first

/**
 * Decides what an export contains: one document as it is, several documents merged into one, or a single page of
 * the first document. Returns null when nothing that was asked for exists any more.
 */
class PrepareExport(
    private val reader: DocumentReader,
) {
    suspend operator fun invoke(
        documentIds: List<Long>,
        pageId: Long? = null,
    ): DocumentWithPages? {
        val documents = documentIds.mapNotNull { reader.observe(it).first() }
        val first = documents.firstOrNull() ?: return null
        return when {
            pageId != null -> first.pages.firstOrNull { it.id == pageId }?.let { onePage(first, it) }
            documents.size == 1 -> first
            else -> {
                val pages = documents.flatMap { it.pages }
                val title = "Scans (${documents.size} documents)"
                DocumentWithPages(first.document.copy(title = title, pageCount = pages.size), pages)
            }
        }
    }

    private fun onePage(
        source: DocumentWithPages,
        page: Page,
    ) = DocumentWithPages(
        source.document.copy(title = "${source.document.title} page ${page.index + 1}", pageCount = 1),
        listOf(page),
    )
}

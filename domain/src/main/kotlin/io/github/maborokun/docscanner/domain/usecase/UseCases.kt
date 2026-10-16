package io.github.maborokun.docscanner.domain.usecase

import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.repository.DocumentReader
import io.github.maborokun.docscanner.domain.repository.DocumentWriter
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import io.github.maborokun.docscanner.domain.repository.PageWriter
import kotlinx.coroutines.flow.Flow

class ObserveDocuments(
    private val repository: DocumentReader,
) {
    operator fun invoke(query: String = ""): Flow<List<Document>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) repository.observeAll() else repository.search(trimmed)
    }
}

/**
 * Imports one image (a content URI string) as a document, running OCR on it, and returns the document id.
 * A blank title becomes the merchant found on the page, or "Scan <date>".
 */
class ImportDocument(
    private val store: PageImageStore,
    private val scanDocument: ScanDocument,
) {
    suspend operator fun invoke(
        source: String,
        title: String = "",
    ): Long = scanDocument(store.import(source), title = title)
}

class DeleteDocument(
    private val repository: DocumentWriter,
    private val store: PageImageStore,
) {
    suspend operator fun invoke(id: Long) {
        store.delete(repository.delete(id))
    }
}

/** Removes one page of a document together with its image file. */
class RemovePage(
    private val repository: PageWriter,
    private val store: PageImageStore,
) {
    suspend operator fun invoke(pageId: Long) {
        store.delete(repository.removePage(pageId))
    }
}

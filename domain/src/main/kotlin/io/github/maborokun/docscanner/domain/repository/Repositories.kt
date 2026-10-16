package io.github.maborokun.docscanner.domain.repository

import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.model.StoredImage
import kotlinx.coroutines.flow.Flow

/** Reading documents; what a list, a detail screen or an export needs. */
interface DocumentReader {
    fun observeAll(): Flow<List<Document>>

    /** Matches titles, merchants, comments and OCR text; a blank [query] matches everything. */
    fun search(query: String): Flow<List<Document>>

    fun observe(id: Long): Flow<DocumentWithPages?>
}

/** Creating, describing and deleting whole documents. */
interface DocumentWriter {
    /** Returns the new document id. */
    suspend fun insert(
        title: String,
        createdAt: Long,
        fields: ReceiptFields?,
        pages: List<NewPage>,
        category: DocumentCategory = DocumentCategory.Document,
    ): Long

    suspend fun updateCategory(
        id: Long,
        category: DocumentCategory,
    )

    suspend fun updateComment(
        id: Long,
        comment: String,
    )

    suspend fun updateFields(
        id: Long,
        fields: ReceiptFields?,
    )

    /** Removes the document and its pages; returns the image paths that were attached to it. */
    suspend fun delete(id: Long): List<String>
}

/** Changing the pages inside a document. */
interface PageWriter {
    /** Appends [page] after the document's last page. Fails when the document does not exist. */
    suspend fun addPage(
        documentId: Long,
        page: NewPage,
    )

    /** Swaps a page's image, text and edits in place; returns the image path it had, or null for an unknown page. */
    suspend fun replacePage(
        pageId: Long,
        page: NewPage,
    ): String?

    /**
     * Removes one page and returns the files that belonged to it (image and original). Returns an empty list, and
     * removes nothing, when the page is unknown or is the document's only page (delete the document instead).
     */
    suspend fun removePage(pageId: Long): List<String>
}

/** Everything the database offers; consumers should ask for the narrow interface they actually use. */
interface DocumentRepository :
    DocumentReader,
    DocumentWriter,
    PageWriter

/** Page image files in app-private storage. */
interface PageImageStore {
    /** Copies the image behind [source] (a content URI string) into private storage. */
    suspend fun import(source: String): StoredImage

    /** A fresh, unused path inside private storage for a processed image to be written to. */
    suspend fun allocatePath(): String

    suspend fun delete(paths: List<String>)
}

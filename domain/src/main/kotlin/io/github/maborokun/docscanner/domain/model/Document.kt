package io.github.maborokun.docscanner.domain.model

import java.time.LocalDate

/** Receipt values the user can edit; [totalMinor] is in minor units (cents). */
data class ReceiptFields(
    val merchant: String? = null,
    val date: LocalDate? = null,
    val totalMinor: Long? = null,
    val currency: String? = null,
    val confidence: Float = 0f,
)

data class Document(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val pageCount: Int,
    val extractedFields: ReceiptFields? = null,
    /** Image of the first page, for list thumbnails. */
    val thumbnailPath: String? = null,
    val category: DocumentCategory = DocumentCategory.Document,
    val comment: String = "",
)

data class Page(
    val id: Long,
    val documentId: Long,
    val index: Int,
    val imagePath: String,
    val width: Int,
    val height: Int,
    val ocrText: String = "",
    val quad: Quad = Quad.FULL,
    /** The upright photo [imagePath] was made from, kept so the crop and edits can be changed later. */
    val originalPath: String? = null,
    val edit: PageEdit = PageEdit(),
)

data class DocumentWithPages(
    val document: Document,
    val pages: List<Page>,
)

/** A page not yet stored, so it has no ids. */
data class NewPage(
    val imagePath: String,
    val width: Int,
    val height: Int,
    val ocrText: String = "",
    val quad: Quad = Quad.FULL,
    val originalPath: String? = null,
    val edit: PageEdit = PageEdit(),
)

/** An image copied into app-private storage. */
data class StoredImage(
    val path: String,
    val width: Int,
    val height: Int,
)

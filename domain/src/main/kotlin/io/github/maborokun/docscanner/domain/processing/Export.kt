package io.github.maborokun.docscanner.domain.processing

import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.Page
import kotlinx.coroutines.flow.Flow

sealed interface ExportProgress {
    /** [done] of [total] pages written so far. */
    data class Pages(
        val done: Int,
        val total: Int,
    ) : ExportProgress

    /** The finished file: [path] on disk, [fileName] to show to the user. */
    data class Finished(
        val path: String,
        val fileName: String,
    ) : ExportProgress
}

/**
 * Writes a document as a multi-page PDF. Collecting starts the work, cancelling the collection stops it and removes
 * the partial file.
 */
interface PdfExporter {
    fun export(document: DocumentWithPages): Flow<ExportProgress>
}

/** Copies one page image to a shareable file with a readable name; returns the finished file. */
interface ImageExporter {
    suspend fun export(
        page: Page,
        title: String,
    ): ExportProgress.Finished
}

package io.github.maborokun.docscanner.appfunctions

import androidx.appfunctions.AppFunctionSerializable
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.receipt.formatMinor
import io.github.maborokun.docscanner.domain.usecase.ObserveDocuments
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId

/**
 * What an assistant may learn about a document: title, date and total only. Never the images, the recognised text
 * or the file paths.
 */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DocumentSummary(
    /** The document title, e.g. the merchant name. */
    val title: String,
    /** When the document was scanned, as an ISO-8601 date like 2026-10-15. */
    val scannedOn: String,
    /** The receipt total with two decimals like 12.50, or null when none was recognised. */
    val total: String?,
    /** The three-letter currency code of the total, for example EUR, or null when unknown. */
    val currency: String?,
    /** How many pages the document has. */
    val pageCount: Int,
)

/** The search behind the `searchDocuments` function; a plain class so it can be tested without Android 16. */
class DocumentSearch(
    private val observeDocuments: ObserveDocuments,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    /** Documents matching [query] (title, merchant or text), newest first; a blank query lists the newest. */
    suspend fun search(query: String): List<DocumentSummary> =
        observeDocuments(query).first().take(MAX_RESULTS).map { it.toSummary() }

    private fun Document.toSummary(): DocumentSummary =
        DocumentSummary(
            title = title,
            scannedOn =
                Instant
                    .ofEpochMilli(createdAt)
                    .atZone(zone)
                    .toLocalDate()
                    .toString(),
            total = extractedFields?.totalMinor?.let(::formatMinor),
            currency = extractedFields?.currency,
            pageCount = pageCount,
        )

    companion object {
        /** An assistant gets a short list, not the whole library. */
        const val MAX_RESULTS = 20
    }
}

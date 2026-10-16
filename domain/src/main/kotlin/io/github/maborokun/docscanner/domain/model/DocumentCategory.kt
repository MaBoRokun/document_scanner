package io.github.maborokun.docscanner.domain.model

/** What a scanned document is. Suggested by [io.github.maborokun.docscanner.domain.classify.DocumentClassifier]. */
enum class DocumentCategory(
    val label: String,
) {
    Receipt("Receipt"),
    Passport("Passport / ID"),
    BookPage("Book page"),
    A4Page("A4 page"),
    Document("Document"),
    Other("Other"),
    ;

    companion object {
        /** The stored name back to a category; anything unknown (an older or newer build) becomes [Document]. */
        fun fromName(name: String?): DocumentCategory = entries.firstOrNull { it.name == name } ?: Document

        /** Categories whose label starts with, or contains, what the user typed; empty for blank input. */
        fun matching(query: String): List<DocumentCategory> {
            val q = query.trim().lowercase()
            if (q.length < MIN_QUERY) return emptyList()
            return entries.filter { it.label.lowercase().contains(q) || it.name.lowercase().startsWith(q) }
        }

        private const val MIN_QUERY = 2
    }
}

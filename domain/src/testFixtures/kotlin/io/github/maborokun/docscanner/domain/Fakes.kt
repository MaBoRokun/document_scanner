package io.github.maborokun.docscanner.domain

import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.FrameSize
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.Page
import io.github.maborokun.docscanner.domain.model.PageEdit
import io.github.maborokun.docscanner.domain.model.Quad
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.model.StoredImage
import io.github.maborokun.docscanner.domain.processing.EnhanceMode
import io.github.maborokun.docscanner.domain.processing.ExportProgress
import io.github.maborokun.docscanner.domain.processing.ImageExporter
import io.github.maborokun.docscanner.domain.processing.PageProcessor
import io.github.maborokun.docscanner.domain.processing.PageRenderer
import io.github.maborokun.docscanner.domain.processing.PdfExporter
import io.github.maborokun.docscanner.domain.processing.TextRecognizer
import io.github.maborokun.docscanner.domain.repository.DocumentRepository
import io.github.maborokun.docscanner.domain.repository.PageImageStore
import io.github.maborokun.docscanner.domain.settings.AppSettings
import io.github.maborokun.docscanner.domain.settings.SettingsRepository
import io.github.maborokun.docscanner.domain.usecase.ImportDocument
import io.github.maborokun.docscanner.domain.usecase.ScanDocument
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.ZoneOffset

/** In-memory repository; search is a case-insensitive substring match over title, merchant and OCR text. */
class FakeDocumentRepository : DocumentRepository {
    private val state = MutableStateFlow<List<DocumentWithPages>>(emptyList())
    private var nextId = 1L
    private var nextPageIndex = 50L
    var failNextInsert = false

    override fun observeAll(): Flow<List<Document>> = state.map { all -> all.map { it.document }.newestFirst() }

    override fun search(query: String): Flow<List<Document>> =
        state.map { all ->
            if (query.isBlank()) {
                all.map { it.document }
            } else {
                all.filter { it.matches(query) }.map { it.document }
            }.newestFirst()
        }

    override fun observe(id: Long): Flow<DocumentWithPages?> =
        state.map { all -> all.firstOrNull { it.document.id == id } }

    override suspend fun insert(
        title: String,
        createdAt: Long,
        fields: ReceiptFields?,
        pages: List<NewPage>,
        category: DocumentCategory,
    ): Long {
        check(!failNextInsert) { "insert failed" }
        val id = nextId++
        val document =
            Document(id, title, createdAt, pages.size, fields, pages.firstOrNull()?.imagePath, category)
        val stored =
            pages.mapIndexed { i, p ->
                Page(
                    id * PAGE_ID_FACTOR + i,
                    id,
                    i,
                    p.imagePath,
                    p.width,
                    p.height,
                    p.ocrText,
                    p.quad,
                    p.originalPath,
                    p.edit,
                )
            }
        state.value += DocumentWithPages(document, stored)
        return id
    }

    override suspend fun addPage(
        documentId: Long,
        page: NewPage,
    ) {
        val current = state.value.firstOrNull { it.document.id == documentId } ?: error("No document $documentId")
        val stored =
            Page(
                documentId * PAGE_ID_FACTOR + nextPageIndex++,
                documentId,
                current.pages.size,
                page.imagePath,
                page.width,
                page.height,
                page.ocrText,
                page.quad,
                page.originalPath,
                page.edit,
            )
        state.value =
            state.value.map {
                if (it.document.id == documentId) {
                    DocumentWithPages(it.document.copy(pageCount = it.pages.size + 1), it.pages + stored)
                } else {
                    it
                }
            }
    }

    override suspend fun removePage(pageId: Long): List<String> {
        val owner = state.value.firstOrNull { doc -> doc.pages.any { it.id == pageId } }
        val removed = owner?.pages?.firstOrNull { it.id == pageId }
        if (owner == null || removed == null || owner.pages.size == 1) return emptyList()
        state.value =
            state.value.map {
                if (it === owner) {
                    DocumentWithPages(it.document.copy(pageCount = it.pages.size - 1), it.pages - removed)
                } else {
                    it
                }
            }
        return listOfNotNull(removed.imagePath, removed.originalPath).distinct()
    }

    override suspend fun updateCategory(
        id: Long,
        category: DocumentCategory,
    ) = updateDocument(id) { it.copy(category = category) }

    override suspend fun updateComment(
        id: Long,
        comment: String,
    ) = updateDocument(id) { it.copy(comment = comment) }

    override suspend fun replacePage(
        pageId: Long,
        page: NewPage,
    ): String? {
        var old: String? = null
        state.value =
            state.value.map { doc ->
                val target = doc.pages.firstOrNull { it.id == pageId } ?: return@map doc
                old = target.imagePath
                val replaced =
                    target.copy(
                        imagePath = page.imagePath,
                        width = page.width,
                        height = page.height,
                        ocrText = page.ocrText,
                        quad = page.quad,
                        originalPath = page.originalPath,
                        edit = page.edit,
                    )
                val pages = doc.pages.map { if (it.id == pageId) replaced else it }
                DocumentWithPages(doc.document.copy(thumbnailPath = pages.first().imagePath), pages)
            }
        return old
    }

    private fun updateDocument(
        id: Long,
        change: (Document) -> Document,
    ) {
        state.value = state.value.map { if (it.document.id == id) it.copy(document = change(it.document)) else it }
    }

    override suspend fun updateFields(
        id: Long,
        fields: ReceiptFields?,
    ) {
        state.value =
            state.value.map {
                if (it.document.id == id) it.copy(document = it.document.copy(extractedFields = fields)) else it
            }
    }

    override suspend fun delete(id: Long): List<String> {
        val removed = state.value.firstOrNull { it.document.id == id }
        state.value = state.value.filterNot { it.document.id == id }
        return removed
            ?.pages
            ?.flatMap { listOfNotNull(it.imagePath, it.originalPath) }
            ?.distinct()
            .orEmpty()
    }

    private fun List<Document>.newestFirst() = sortedByDescending { it.createdAt }

    private fun DocumentWithPages.matches(query: String): Boolean {
        val q = query.lowercase()
        return document.title.lowercase().contains(q) ||
            document.extractedFields
                ?.merchant
                ?.lowercase()
                ?.contains(q) == true ||
            pages.any { it.ocrText.lowercase().contains(q) }
    }
}

class FakePageImageStore : PageImageStore {
    val deleted = mutableListOf<String>()
    var failImport = false

    override suspend fun import(source: String): StoredImage {
        check(!failImport) { "import failed" }
        return StoredImage("/private/${source.substringAfterLast('/')}", 800, 1200)
    }

    private var allocated = 0
    val allocatedPaths = mutableListOf<String>()

    override suspend fun allocatePath(): String = "/private/alloc-${++allocated}.jpg".also { allocatedPaths += it }

    override suspend fun delete(paths: List<String>) {
        deleted += paths
    }
}

/** Records calls and pretends every warp produces an [outputSize] image. */
class FakePageProcessor(
    private val outputSize: FrameSize = FrameSize(600, 800),
) : PageProcessor {
    val warps = mutableListOf<Triple<String, Quad, String>>()
    val enhances = mutableListOf<Triple<String, EnhanceMode, String>>()
    var failEnhance = false

    override suspend fun warp(
        input: String,
        quad: Quad,
        output: String,
    ): FrameSize {
        warps += Triple(input, quad, output)
        return outputSize
    }

    override suspend fun enhance(
        input: String,
        mode: EnhanceMode,
        output: String,
    ) {
        check(!failEnhance) { "enhance failed" }
        enhances += Triple(input, mode, output)
    }
}

/** Records renders and pretends each one produces an [outputSize] image. */
class FakePageRenderer(
    private val outputSize: FrameSize = FrameSize(600, 800),
) : PageRenderer {
    val renders = mutableListOf<Triple<String, Quad, PageEdit>>()
    var fail = false

    override suspend fun render(
        original: String,
        quad: Quad,
        edit: PageEdit,
        output: String,
    ): FrameSize {
        check(!fail) { "render failed" }
        renders += Triple(original, quad, edit)
        return outputSize
    }
}

class FakeImageExporter : ImageExporter {
    val exported = mutableListOf<Page>()

    override suspend fun export(
        page: Page,
        title: String,
    ): ExportProgress.Finished {
        exported += page
        return ExportProgress.Finished("/cache/$title.jpg", "$title.jpg")
    }
}

class FakeTextRecognizer(
    var text: String = "",
) : TextRecognizer {
    var fail = false
    val recognized = mutableListOf<String>()

    override suspend fun recognize(imagePath: String): String {
        check(!fail) { "ocr failed" }
        recognized += imagePath
        return text
    }
}

/** [ImportDocument] wired to fakes (UTC, no processing, empty OCR unless [recognizer] says otherwise). */
fun fakeImportDocument(
    repository: DocumentRepository,
    store: PageImageStore,
    recognizer: TextRecognizer = FakeTextRecognizer(),
    clock: () -> Long,
): ImportDocument =
    ImportDocument(
        store,
        ScanDocument(FakePageProcessor(), recognizer, repository, repository, store, ZoneOffset.UTC, clock = clock),
    )

private const val PAGE_ID_FACTOR = 100L

/** Emits scripted progress, then a finished file; suspends forever when [hang] is set (to test cancellation). */
class FakePdfExporter(
    private val hang: Boolean = false,
) : PdfExporter {
    var exported = 0
        private set
    var cancelled = false
        private set

    override fun export(document: DocumentWithPages): Flow<ExportProgress> =
        flow {
            try {
                val total = document.pages.size
                for (i in 1..total) {
                    emit(ExportProgress.Pages(i, total))
                    if (hang) awaitCancellation()
                }
                exported++
                emit(ExportProgress.Finished("/cache/${document.document.title}.pdf", "${document.document.title}.pdf"))
            } finally {
                if (hang) cancelled = true
            }
        }
}

class FakeSettingsRepository(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        state.value = change(state.value)
    }
}

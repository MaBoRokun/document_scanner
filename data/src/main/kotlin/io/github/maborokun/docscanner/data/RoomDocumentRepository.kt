package io.github.maborokun.docscanner.data

import android.content.Context
import androidx.room.Room
import io.github.maborokun.docscanner.domain.model.Document
import io.github.maborokun.docscanner.domain.model.DocumentCategory
import io.github.maborokun.docscanner.domain.model.DocumentWithPages
import io.github.maborokun.docscanner.domain.model.NewPage
import io.github.maborokun.docscanner.domain.model.ReceiptFields
import io.github.maborokun.docscanner.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class RoomDocumentRepository internal constructor(
    private val database: DocScannerDatabase,
) : DocumentRepository {
    private val dao = database.documentDao()

    override fun observeAll(): Flow<List<Document>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun search(query: String): Flow<List<Document>> {
        val fts = ftsQuery(query) ?: return observeAll()
        return dao.search(likePattern(query.trim()), fts).map { rows -> rows.map { it.toDomain() } }
    }

    override fun observe(id: Long): Flow<DocumentWithPages?> =
        combine(dao.observeDocument(id), dao.observePages(id)) { document, pages ->
            document?.let {
                val domainPages = pages.map { page -> page.toDomain() }
                DocumentWithPages(
                    Document(
                        it.id,
                        it.title,
                        it.createdAt,
                        domainPages.size,
                        it.toFields(),
                        domainPages.firstOrNull()?.imagePath,
                        DocumentCategory.fromName(it.category),
                        it.comment,
                    ),
                    domainPages,
                )
            }
        }

    override suspend fun insert(
        title: String,
        createdAt: Long,
        fields: ReceiptFields?,
        pages: List<NewPage>,
        category: DocumentCategory,
    ): Long {
        val document = (fields ?: ReceiptFields()).toEntity(title, createdAt, category)
        return dao.insertWithPages(document, pages.mapIndexed { i, page -> page.toEntity(i) })
    }

    override suspend fun addPage(
        documentId: Long,
        page: NewPage,
    ) = dao.appendPage(documentId, page.toEntity(0))

    override suspend fun removePage(pageId: Long): List<String> = dao.removeUnlessLast(pageId)

    override suspend fun updateCategory(
        id: Long,
        category: DocumentCategory,
    ) = dao.updateCategory(id, category.name)

    override suspend fun updateComment(
        id: Long,
        comment: String,
    ) = dao.updateComment(id, comment)

    override suspend fun replacePage(
        pageId: Long,
        page: NewPage,
    ): String? = dao.replacePage(pageId, page.toEntity(0))

    override suspend fun updateFields(
        id: Long,
        fields: ReceiptFields?,
    ) {
        val f = fields ?: ReceiptFields()
        dao.updateFields(id, f.merchant, f.totalMinor, f.currency, f.date?.toString(), f.confidence)
    }

    /** Closes the database; only tests need this, the app keeps it open for its lifetime. */
    internal fun close() = database.close()

    override suspend fun delete(id: Long): List<String> = dao.deleteReturningPaths(id)

    companion object {
        fun create(context: Context): RoomDocumentRepository =
            RoomDocumentRepository(
                Room
                    .databaseBuilder(
                        context.applicationContext,
                        DocScannerDatabase::class.java,
                        "docscanner.db",
                    ).addMigrations(MIGRATION_1_2)
                    .build(),
            )

        /** In-memory database for tests. */
        internal fun inMemory(context: Context): RoomDocumentRepository =
            RoomDocumentRepository(
                Room.inMemoryDatabaseBuilder(context, DocScannerDatabase::class.java).allowMainThreadQueries().build(),
            )
    }
}

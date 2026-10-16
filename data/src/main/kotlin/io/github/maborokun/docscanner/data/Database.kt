package io.github.maborokun.docscanner.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "document")
internal data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val merchant: String?,
    val totalMinor: Long?,
    val currency: String?,
    /** ISO-8601 date, e.g. 2026-10-15. */
    val date: String?,
    val confidence: Float,
    /** A [io.github.maborokun.docscanner.domain.model.DocumentCategory] name. */
    @ColumnInfo(defaultValue = "Document") val category: String = "Document",
    @ColumnInfo(defaultValue = "") val comment: String = "",
)

@Entity(
    tableName = "page",
    foreignKeys = [
        ForeignKey(
            entity = DocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("documentId")],
)
internal data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long,
    @ColumnInfo(name = "idx") val index: Int,
    val imagePath: String,
    val width: Int,
    val height: Int,
    val quadJson: String,
    val ocrText: String,
    val originalPath: String? = null,
    /** JSON of the page's edits; empty when there are none. */
    @ColumnInfo(defaultValue = "") val editJson: String = "",
)

/** Full-text index over [PageEntity.ocrText]; Room keeps it in sync through triggers. */
@Fts4(contentEntity = PageEntity::class)
@Entity(tableName = "page_fts")
internal data class PageFts(
    val ocrText: String,
)

internal data class DocumentRow(
    @Embedded val document: DocumentEntity,
    val pageCount: Int,
    val thumbnailPath: String?,
)

private const val ROW_COLUMNS =
    "d.*, (SELECT COUNT(*) FROM page WHERE documentId = d.id) AS pageCount, " +
        "(SELECT imagePath FROM page WHERE documentId = d.id ORDER BY idx LIMIT 1) AS thumbnailPath"

@Dao
internal interface DocumentDao {
    @Query(
        "SELECT $ROW_COLUMNS " +
            "FROM document d ORDER BY d.createdAt DESC, d.id DESC",
    )
    fun observeAll(): Flow<List<DocumentRow>>

    /** [like] is an already-escaped LIKE pattern, [fts] a sanitized FTS4 MATCH expression. */
    @Query(
        "SELECT $ROW_COLUMNS FROM document d " +
            "WHERE d.title LIKE :like ESCAPE '\\' OR d.merchant LIKE :like ESCAPE '\\' " +
            "OR d.comment LIKE :like ESCAPE '\\' " +
            "OR d.id IN (SELECT p.documentId FROM page p " +
            "WHERE p.id IN (SELECT rowid FROM page_fts WHERE page_fts MATCH :fts)) " +
            "ORDER BY d.createdAt DESC, d.id DESC",
    )
    fun search(
        like: String,
        fts: String,
    ): Flow<List<DocumentRow>>

    @Query("SELECT * FROM document WHERE id = :id")
    fun observeDocument(id: Long): Flow<DocumentEntity?>

    @Query("SELECT * FROM page WHERE documentId = :id ORDER BY idx")
    fun observePages(id: Long): Flow<List<PageEntity>>

    /** Every file a document's pages use: the shown images and the kept originals. */
    @Query(
        "SELECT imagePath FROM page WHERE documentId = :id " +
            "UNION SELECT originalPath FROM page WHERE documentId = :id AND originalPath IS NOT NULL",
    )
    suspend fun imagePaths(id: Long): List<String>

    @Query("UPDATE document SET category = :category WHERE id = :id")
    suspend fun updateCategory(
        id: Long,
        category: String,
    )

    @Query("UPDATE document SET comment = :comment WHERE id = :id")
    suspend fun updateComment(
        id: Long,
        comment: String,
    )

    @Query(
        "UPDATE page SET imagePath = :imagePath, originalPath = :originalPath, width = :width, height = :height, " +
            "quadJson = :quadJson, ocrText = :ocrText, editJson = :editJson WHERE id = :pageId",
    )
    suspend fun updatePage(
        pageId: Long,
        imagePath: String,
        originalPath: String?,
        width: Int,
        height: Int,
        quadJson: String,
        ocrText: String,
        editJson: String,
    )

    @Transaction
    suspend fun replacePage(
        pageId: Long,
        page: PageEntity,
    ): String? {
        val old = page(pageId) ?: return null
        updatePage(
            pageId,
            page.imagePath,
            page.originalPath,
            page.width,
            page.height,
            page.quadJson,
            page.ocrText,
            page.editJson,
        )
        return old.imagePath
    }

    @Insert
    suspend fun insertDocument(document: DocumentEntity): Long

    @Insert
    suspend fun insertPages(pages: List<PageEntity>)

    @Query(
        "UPDATE document SET merchant = :merchant, totalMinor = :totalMinor, currency = :currency, " +
            "date = :date, confidence = :confidence WHERE id = :id",
    )
    suspend fun updateFields(
        id: Long,
        merchant: String?,
        totalMinor: Long?,
        currency: String?,
        date: String?,
        confidence: Float,
    )

    @Query("DELETE FROM document WHERE id = :id")
    suspend fun deleteDocument(id: Long)

    @Insert
    suspend fun insertPage(page: PageEntity): Long

    @Query("SELECT COALESCE(MAX(idx), -1) + 1 FROM page WHERE documentId = :documentId")
    suspend fun nextIndex(documentId: Long): Int

    @Query("SELECT * FROM page WHERE id = :pageId")
    suspend fun page(pageId: Long): PageEntity?

    @Query("SELECT COUNT(*) FROM page WHERE documentId = :documentId")
    suspend fun pageCount(documentId: Long): Int

    @Query("DELETE FROM page WHERE id = :pageId")
    suspend fun deletePage(pageId: Long)

    /** Fails with a constraint error when [documentId] does not exist. */
    @Transaction
    suspend fun appendPage(
        documentId: Long,
        page: PageEntity,
    ) {
        insertPage(page.copy(documentId = documentId, index = nextIndex(documentId)))
    }

    @Transaction
    suspend fun removeUnlessLast(pageId: Long): List<String> {
        val page = page(pageId)
        return if (page != null && pageCount(page.documentId) > 1) {
            deletePage(pageId)
            listOfNotNull(page.imagePath, page.originalPath).distinct()
        } else {
            emptyList()
        }
    }

    @Transaction
    suspend fun insertWithPages(
        document: DocumentEntity,
        pages: List<PageEntity>,
    ): Long {
        val id = insertDocument(document)
        insertPages(pages.map { it.copy(documentId = id) })
        return id
    }

    @Transaction
    suspend fun deleteReturningPaths(id: Long): List<String> {
        val paths = imagePaths(id)
        deleteDocument(id)
        return paths
    }
}

@Database(entities = [DocumentEntity::class, PageEntity::class, PageFts::class], version = 2)
internal abstract class DocScannerDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}

/** v2: document category and comment, and the original photo and edits of each page. */
internal val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE document ADD COLUMN category TEXT NOT NULL DEFAULT 'Document'")
            db.execSQL("ALTER TABLE document ADD COLUMN comment TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE page ADD COLUMN originalPath TEXT")
            db.execSQL("ALTER TABLE page ADD COLUMN editJson TEXT NOT NULL DEFAULT ''")
        }
    }

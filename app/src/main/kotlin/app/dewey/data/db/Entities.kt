package app.dewey.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [Index(value = ["uri"], unique = true)],
)
data class DocumentRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SAF content URI. Unique: re-importing the same document updates it. */
    val uri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "last_modified") val lastModified: Long,
    @ColumnInfo(name = "page_count") val pageCount: Int,
    @ColumnInfo(name = "doc_type") val docType: String,
    val language: String?,
    @ColumnInfo(name = "text_source") val textSource: String,
    /**
     * Full extracted text. Held so re-chunking and re-classification never need
     * to re-open the PDF — reopening means an OCR pass again on scanned files,
     * which is by far the most expensive thing the app does.
     */
    val text: String?,
    @ColumnInfo(name = "indexed_at") val indexedAt: Long?,
    /**
     * Why this document could not be filed confidently, or null if it could.
     * Stored rather than recomputed so the review queue survives a restart
     * without re-running the classifier over the whole library.
     */
    @ColumnInfo(name = "review_reason") val reviewReason: String? = null,
    /** How far the winning category beat the runner-up. Shown while reviewing. */
    @ColumnInfo(name = "classify_margin") val classifyMargin: Float? = null,
    /** The folder this document was moved into, if it was. */
    @ColumnInfo(name = "sorted_folder") val sortedFolder: String? = null,
    /**
     * Fields pulled out of [text] by FieldExtractor at import time - see
     * app.dewey.extract.FieldExtractor. Computed once and stored rather than
     * recomputed on read for the same reason [text] itself is: re-deriving
     * these from a scanned document's text means nothing without the text
     * being held anyway, but the library screen reads them on every
     * recomposition and should not be re-running regexes to do it.
     */
    @ColumnInfo(name = "vendor") val vendor: String? = null,
    @ColumnInfo(name = "amount") val amount: Double? = null,
    @ColumnInfo(name = "currency") val currency: String? = null,
    /**
     * Stored as an epoch day ([java.time.LocalDate.toEpochDay]), not epoch
     * millis - these are calendar dates with no time-of-day, and an epoch day
     * has no time zone to get wrong on a device that reads the SD card in one
     * zone and opens the app in another.
     */
    @ColumnInfo(name = "issue_date") val issueDateEpochDay: Long? = null,
    @ColumnInfo(name = "due_date") val dueDateEpochDay: Long? = null,
)

/**
 * A note the user wrote, standalone or attached to a bill.
 *
 * [billDocumentId] is left un-renamed by [ColumnInfo] deliberately, unlike
 * the rest of this file's snake_case columns — its default column name is
 * what fixes the migration's index name at `index_notes_billDocumentId`, and
 * a hand-written migration is exactly the place a silent rename would only
 * surface as a runtime crash on someone's real device.
 */
@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            entity = DocumentRow::class,
            parentColumns = ["id"],
            childColumns = ["billDocumentId"],
            // SET NULL, not CASCADE: deleting the document a note is about
            // should leave the note as a standalone one, never delete it too.
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["billDocumentId"])],
)
data class NoteRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    /** The `documents` row this note is about, or null for a standalone note. */
    val billDocumentId: Long? = null,
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "chunks",
    foreignKeys = [
        ForeignKey(
            entity = DocumentRow::class,
            parentColumns = ["id"],
            childColumns = ["document_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["document_id"])],
)
data class ChunkRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "document_id") val documentId: Long,
    val ordinal: Int,
    val text: String,
    /** Little-endian float32, packed. See [FloatArrayCodec]. */
    val embedding: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChunkRow) return false
        return id == other.id &&
            documentId == other.documentId &&
            ordinal == other.ordinal &&
            text == other.text &&
            embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + documentId.hashCode()
        result = 31 * result + ordinal
        result = 31 * result + text.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}

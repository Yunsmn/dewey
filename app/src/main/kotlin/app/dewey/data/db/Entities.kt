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

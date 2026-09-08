package app.dewey.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The identity of an indexed document plus the opening of its text.
 *
 * A projection rather than a slice of [DocumentRow]: Room maps a query's
 * columns onto whatever class is asked for, so naming the three columns a
 * whole-library classification pass needs keeps the other twelve — the stored
 * text above all — out of memory entirely.
 */
data class DocumentOpening(
    val id: Long,
    val uri: String,
    /** Null when the document was indexed but nothing readable came out of it. */
    val opening: String?,
)

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY last_modified DESC")
    fun observeAll(): Flow<List<DocumentRow>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun byId(id: Long): DocumentRow?

    @Query("SELECT * FROM documents WHERE uri = :uri")
    suspend fun byUri(uri: String): DocumentRow?

    @Query("SELECT id FROM documents WHERE indexed_at IS NULL")
    suspend fun unindexedIds(): List<Long>

    @Query("SELECT COUNT(*) FROM documents")
    fun observeCount(): Flow<Int>

    @Upsert
    suspend fun upsert(row: DocumentRow): Long

    @Query("UPDATE documents SET indexed_at = :at WHERE id = :id")
    suspend fun markIndexed(id: Long, at: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * Every indexed document, carrying only the opening of its text.
     *
     * Deliberately not `SELECT *`. A document's stored text runs to 200,000
     * characters, which is 400KB of heap per row as a Java String; four hundred
     * of them is 160MB, and a sort would die of it on any phone before it filed
     * anything. The classifier reads the first
     * [app.dewey.classify.DocumentClassifier.OPENING_CHARS] characters and
     * nothing else, so that is what this returns — the same query at a
     * thousandth of the memory.
     *
     * SQLite's substr() is 1-based and clamps to the string's length, so a
     * short document comes back whole and a null text stays null.
     */
    @Query(
        "SELECT id, uri, substr(text, 1, :openingChars) AS opening " +
            "FROM documents WHERE indexed_at IS NOT NULL"
    )
    suspend fun allIndexedOpenings(openingChars: Int): List<DocumentOpening>

    @Query("SELECT * FROM documents WHERE review_reason IS NOT NULL ORDER BY classify_margin ASC")
    fun observeNeedingReview(): Flow<List<DocumentRow>>

    /**
     * A bill is a document with both a due date and an amount - see
     * app.dewey.ui.bills.BillGrouping. Ordered soonest-due-first here so the
     * grouping layer only ever has to bucket an already-sorted list, never
     * re-sort it.
     */
    @Query(
        """
        SELECT * FROM documents
        WHERE due_date IS NOT NULL AND amount IS NOT NULL
        ORDER BY due_date ASC
        """
    )
    fun observeBills(): Flow<List<DocumentRow>>

    @Query(
        """
        UPDATE documents
        SET doc_type = :docType,
            review_reason = :reviewReason,
            classify_margin = :margin
        WHERE id = :id
        """
    )
    suspend fun recordClassification(id: Long, docType: String, reviewReason: String?, margin: Float?)

    @Query("UPDATE documents SET uri = :uri, sorted_folder = :folder WHERE id = :id")
    suspend fun recordMove(id: Long, uri: String, folder: String?)

    /**
     * A document already sitting in one of the app's category folders.
     *
     * Separate from [recordMove] because nothing moved: only what we know about
     * the document changed. The URI is deliberately not touched — it is already
     * right — and review_reason is cleared, because a document whose folder
     * names its category is not waiting on anybody.
     */
    @Query("UPDATE documents SET doc_type = :docType, sorted_folder = :folder, review_reason = NULL WHERE id = :id")
    suspend fun recordFiledInPlace(id: Long, docType: String, folder: String)
}

@Dao
interface ChunkDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(rows: List<ChunkRow>)

    @Query("DELETE FROM chunks WHERE document_id = :documentId")
    suspend fun deleteForDocument(documentId: Long)

    /**
     * Every chunk, for brute-force similarity search.
     *
     * This looks alarming and is not. A few hundred documents produce a few
     * thousand chunks; at 384 dimensions that is a couple of million multiply-adds,
     * which is single-digit milliseconds. An approximate index (sqlite-vec, HNSW)
     * would add a dependency, a build step and an accuracy cliff to solve a
     * problem this corpus does not have. Revisit past ~50k chunks.
     *
     * Ordered explicitly. Without ORDER BY, row order is SQLite's to choose,
     * and DocumentSearch caches a BM25 index positionally against this list —
     * see LexicalIndexCache. The same rows in a different order would score the
     * wrong chunks.
     */
    @Query("SELECT * FROM chunks ORDER BY id")
    suspend fun allChunks(): List<ChunkRow>

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceForDocument(documentId: Long, rows: List<ChunkRow>) {
        deleteForDocument(documentId)
        insertAll(rows)
    }
}

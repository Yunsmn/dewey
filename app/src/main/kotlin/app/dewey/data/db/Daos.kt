package app.dewey.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

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

    @Query("SELECT * FROM documents WHERE indexed_at IS NOT NULL")
    suspend fun allIndexed(): List<DocumentRow>

    @Query("SELECT * FROM documents WHERE review_reason IS NOT NULL ORDER BY classify_margin ASC")
    fun observeNeedingReview(): Flow<List<DocumentRow>>

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
     */
    @Query("SELECT * FROM chunks")
    suspend fun allChunks(): List<ChunkRow>

    @Query("SELECT COUNT(*) FROM chunks")
    suspend fun count(): Int

    @Transaction
    suspend fun replaceForDocument(documentId: Long, rows: List<ChunkRow>) {
        deleteForDocument(documentId)
        insertAll(rows)
    }
}

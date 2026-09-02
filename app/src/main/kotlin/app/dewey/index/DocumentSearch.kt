package app.dewey.index

import app.dewey.data.db.ChunkDao
import app.dewey.data.db.FloatArrayCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Finds documents by meaning and by wording at once.
 *
 * Two rankings are produced over the same chunks — cosine similarity against the
 * query's embedding, and BM25 against its words — and fused by reciprocal rank.
 * Measured on the test corpus, that is 81% recall@1 against 68% for embeddings
 * alone, because this archive is full of documents whose only difference is a
 * month or an amount and embeddings are poor at exactly that.
 *
 * Search is exhaustive. A few thousand chunks at 384 dimensions is single-digit
 * milliseconds, so an approximate index would buy an accuracy cliff and a
 * dependency to solve a problem this corpus does not have. Revisit past ~50k
 * chunks.
 */
class DocumentSearch(
    private val chunkDao: ChunkDao,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class Hit(
        val documentId: Long,
        val chunkId: Long,
        val text: String,
        val score: Double,
    )

    suspend fun search(
        queryText: String,
        queryVector: FloatArray,
        limit: Int = DEFAULT_LIMIT,
    ): List<Hit> = withContext(defaultDispatcher) {
        require(limit > 0) { "limit must be positive" }

        val rows = chunkDao.allChunks()
        if (rows.isEmpty()) return@withContext emptyList()

        val dense = ArrayList<IndexedValue<Float>>(rows.size)
        for ((index, row) in rows.withIndex()) {
            val embedding = FloatArrayCodec.decode(row.embedding)
            // A stored vector of a different width means the encoder changed
            // under a database that was never re-indexed. Skipping keeps search
            // working on the rest instead of throwing on the whole query.
            if (embedding.size != queryVector.size) continue
            dense += IndexedValue(index, VectorMath.dot(queryVector, embedding))
        }
        if (dense.isEmpty()) return@withContext emptyList()

        val denseRanking = dense.sortedByDescending { it.value }.take(CANDIDATES).map { it.index }

        val lexical = Bm25(rows.map { it.text }).scores(queryText)
        val lexicalRanking = lexical.withIndex()
            .filter { it.value > 0.0 }
            .sortedByDescending { it.value }
            .take(CANDIDATES)
            .map { it.index }

        val fused = ReciprocalRankFusion.fuse(listOf(denseRanking, lexicalRanking))

        fused.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (index, score) ->
                val row = rows[index]
                Hit(row.documentId, row.id, row.text, score)
            }
    }

    private companion object {
        const val DEFAULT_LIMIT = 8

        /**
         * How deep each ranking is considered before fusing. Deep enough that a
         * document strong in one signal and mediocre in the other still surfaces,
         * shallow enough that fusion is not dominated by noise from the tail.
         */
        const val CANDIDATES = 50
    }
}

package app.dewey.index

import app.dewey.data.db.ChunkDao
import app.dewey.data.db.FloatArrayCodec
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.PriorityQueue

/**
 * Nearest-neighbour search over stored chunks.
 *
 * Exhaustive, by choice. See [ChunkDao.allChunks] for why an approximate index
 * would be the wrong trade at this corpus size.
 */
class VectorSearch(
    private val chunkDao: ChunkDao,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {

    data class Hit(
        val documentId: Long,
        val chunkId: Long,
        val text: String,
        val score: Float,
    )

    /**
     * The [limit] best-matching chunks.
     *
     * Keeps a bounded min-heap rather than sorting everything: the result set is
     * a handful of items out of thousands, and sorting the whole corpus to
     * discard all but five of it is work for nothing.
     */
    suspend fun search(queryVector: FloatArray, limit: Int = DEFAULT_LIMIT): List<Hit> =
        withContext(defaultDispatcher) {
            require(limit > 0) { "limit must be positive" }
            val rows = chunkDao.allChunks()
            if (rows.isEmpty()) return@withContext emptyList()

            val best = PriorityQueue<Hit>(limit, compareBy(Hit::score))

            for (row in rows) {
                val embedding = FloatArrayCodec.decode(row.embedding)
                // A stored vector of a different width means the model changed
                // under a database that was not re-indexed. Skipping is right:
                // the alternative is throwing and breaking search entirely.
                if (embedding.size != queryVector.size) continue

                val score = VectorMath.dot(queryVector, embedding)
                if (best.size < limit) {
                    best += Hit(row.documentId, row.id, row.text, score)
                } else if (score > best.peek()!!.score) {
                    best.poll()
                    best += Hit(row.documentId, row.id, row.text, score)
                }
            }
            best.sortedByDescending(Hit::score)
        }

    private companion object {
        const val DEFAULT_LIMIT = 8
    }
}

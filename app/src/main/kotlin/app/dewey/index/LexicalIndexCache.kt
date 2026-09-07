package app.dewey.index

/**
 * Keeps the BM25 index between queries.
 *
 * [Bm25] tokenises the whole corpus in its constructor. Building it per query
 * was fine while search ran once a screen, but the answer flow issues several
 * queries for one question and a library of a few thousand chunks re-tokenises
 * on each — work that is identical every time because the corpus has not
 * changed between them.
 *
 * The corpus is identified by how many chunks there are and the highest chunk
 * id, both of which the caller already has in hand. Chunk ids are assigned by
 * the database and never reused, and a document is re-indexed by deleting its
 * chunks and inserting new ones, so any write moves at least one of the two:
 * an insert raises the newest id, a delete lowers the count, and a re-index
 * does both. Nothing edits a chunk's text in place — that is the one change
 * this would not notice, and [app.dewey.data.db.ChunkDao] has no way to do it.
 */
internal class LexicalIndexCache(private val build: (List<String>) -> Bm25 = ::Bm25) {

    private class Entry(val count: Int, val newestId: Long, val index: Bm25)

    // Volatile rather than synchronized: two searches racing at once may each
    // build an index and one publication wins, which costs a duplicated build
    // and never a wrong answer. Holding a lock across the build would make one
    // search wait on the other's tokenisation instead.
    @Volatile
    private var entry: Entry? = null

    /**
     * @param texts called only on a miss, so an unchanged corpus does not pay
     *   for materialising the chunk texts either.
     */
    fun index(count: Int, newestId: Long, texts: () -> List<String>): Bm25 {
        entry?.let { if (it.count == count && it.newestId == newestId) return it.index }

        val built = build(texts())
        entry = Entry(count, newestId, built)
        return built
    }
}

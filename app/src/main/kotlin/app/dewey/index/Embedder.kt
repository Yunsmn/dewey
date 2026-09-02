package app.dewey.index

/**
 * Turns text into a vector.
 *
 * An interface because the encoder is the one part of retrieval most likely to
 * be swapped — and because tests need a deterministic stand-in that does not
 * load a 120MB model.
 */
interface Embedder {

    /** Vector width. Fixed for a given model; callers size buffers from it. */
    val dimensions: Int

    /**
     * Embeds passages for storage.
     *
     * Batched because per-call overhead dominates on short chunks, and indexing
     * runs over thousands of them.
     */
    suspend fun embedPassages(texts: List<String>): List<FloatArray>

    /**
     * Embeds a user's question.
     *
     * Separate from [embedPassages] deliberately: e5-family models are trained
     * with distinct `query:` and `passage:` prefixes, and using the wrong one
     * measurably degrades retrieval. Making it a separate method means a caller
     * cannot get it wrong by forgetting an argument.
     */
    suspend fun embedQuery(text: String): FloatArray

    fun close() {}
}

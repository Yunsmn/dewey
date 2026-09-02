package app.dewey.index

/**
 * Splits document text into overlapping passages for embedding.
 *
 * Chunks are sized in characters rather than tokens. It is an approximation, but
 * the encoder truncates anyway, and a character budget behaves predictably
 * across French, Arabic and English — a token budget does not, because the same
 * sentence costs very different token counts in each.
 *
 * Overlap exists because the interesting sentence is often exactly on a
 * boundary. A bill's due date and its amount can land in different chunks, and
 * a query mentioning both would then match neither well.
 */
class Chunker(
    private val targetSize: Int = TARGET_SIZE,
    private val overlap: Int = OVERLAP,
) {
    init {
        require(targetSize > 0) { "targetSize must be positive" }
        require(overlap in 0 until targetSize) { "overlap must be smaller than targetSize" }
    }

    fun chunk(text: String): List<String> {
        val normalised = text.replace(WHITESPACE_RUN, " ").trim()
        if (normalised.isEmpty()) return emptyList()
        if (normalised.length <= targetSize) return listOf(normalised)

        val chunks = mutableListOf<String>()
        var start = 0

        while (start < normalised.length) {
            val hardEnd = minOf(start + targetSize, normalised.length)
            val end = if (hardEnd == normalised.length) hardEnd else breakNear(normalised, start, hardEnd)

            normalised.substring(start, end).trim()
                .takeIf { it.isNotEmpty() }
                ?.let(chunks::add)

            if (end >= normalised.length) break
            // Step forward by at least one character even in the pathological case
            // where the break point lands inside the overlap window, or this loops.
            start = maxOf(end - overlap, start + 1)
        }
        return chunks
    }

    /**
     * Prefers to cut at a sentence end, then at a space, rather than mid-word.
     * Only looks backwards within [SEARCH_WINDOW]; beyond that the cut would be
     * so early that chunks become uselessly short.
     */
    private fun breakNear(text: String, start: Int, hardEnd: Int): Int {
        val floor = maxOf(start + targetSize - SEARCH_WINDOW, start + 1)

        for (index in hardEnd - 1 downTo floor) {
            if (text[index] in SENTENCE_ENDS) return index + 1
        }
        for (index in hardEnd - 1 downTo floor) {
            if (text[index] == ' ') return index + 1
        }
        return hardEnd
    }

    private companion object {
        const val TARGET_SIZE = 900
        const val OVERLAP = 150
        const val SEARCH_WINDOW = 250

        // Includes the Arabic full stop and question mark: a French/Arabic corpus
        // that only breaks on ASCII punctuation cuts Arabic mid-sentence.
        val SENTENCE_ENDS = charArrayOf('.', '!', '?', '۔', '؟', '\n')
        val WHITESPACE_RUN = Regex("\\s+")
    }
}

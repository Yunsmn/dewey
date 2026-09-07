package app.dewey.index

import kotlin.math.ln

/**
 * Lexical scoring over the chunks already loaded for vector search.
 *
 * Embeddings are strong on topic and weak on exact tokens. In this corpus that
 * is the central case rather than an edge one: almost every document has
 * siblings differing only by month or amount, so "Attijariwafa statement for
 * janvier 2024" reliably retrieves a bank statement and unreliably the right
 * one. BM25 has the opposite bias, and fusing the two measured 68% to 81%
 * recall@1 in tools/eval/retrieval_bench.py.
 *
 * Built in memory from the chunks already loaded for the dense pass, rather
 * than persisted — an FTS table would add work to every insert to save work
 * this does in a few milliseconds. It is held across queries by
 * [LexicalIndexCache] so a corpus that has not changed is tokenised once
 * rather than once per question.
 */
class Bm25(documents: List<String>) {

    private val termFrequencies: List<Map<String, Int>>
    private val lengths: IntArray
    private val averageLength: Double
    private val inverseDocumentFrequency: Map<String, Double>

    init {
        val tokenised = documents.map(::tokenise)
        termFrequencies = tokenised.map { tokens -> tokens.groupingBy { it }.eachCount() }
        lengths = IntArray(tokenised.size) { tokenised[it].size }
        averageLength = if (lengths.isEmpty()) 0.0 else lengths.sum().toDouble() / lengths.size

        val documentFrequency = HashMap<String, Int>()
        for (tokens in tokenised) {
            for (term in tokens.toHashSet()) {
                documentFrequency[term] = (documentFrequency[term] ?: 0) + 1
            }
        }

        val total = tokenised.size
        inverseDocumentFrequency = documentFrequency.mapValues { (_, frequency) ->
            ln(1.0 + (total - frequency + 0.5) / (frequency + 0.5))
        }
    }

    val size: Int get() = lengths.size

    fun scores(query: String): DoubleArray {
        val terms = tokenise(query)
        val out = DoubleArray(lengths.size)
        if (terms.isEmpty() || averageLength <= 0.0) return out

        for (index in lengths.indices) {
            val frequencies = termFrequencies[index]
            val normalisation = K1 * (1 - B + B * lengths[index] / averageLength)
            var total = 0.0
            for (term in terms) {
                val frequency = frequencies[term] ?: continue
                val idf = inverseDocumentFrequency[term] ?: continue
                total += idf * frequency * (K1 + 1) / (frequency + normalisation)
            }
            out[index] = total
        }
        return out
    }

    private companion object {
        const val K1 = 1.5
        const val B = 0.75

        /**
         * Unicode letters and digits. Java's `\w` is ASCII-only unless told
         * otherwise, which would silently drop every Arabic term in the corpus
         * and leave lexical search working perfectly on French and not at all
         * on a third of the documents.
         */
        val WORD = Regex("""[\p{L}\p{N}]+""")

        fun tokenise(text: String): List<String> =
            WORD.findAll(text.lowercase()).map { it.value }.toList()
    }
}

/**
 * Combines rankings by position rather than by score.
 *
 * Deliberately not a weighted sum of raw scores: BM25 is unbounded while cosine
 * lives in [-1, 1], so blending them directly means inventing a scale factor and
 * then tuning it against one corpus until it overfits. Rank fusion has a single
 * parameter and no units.
 */
object ReciprocalRankFusion {

    /** Damping constant. 60 is the value from the original paper. */
    const val K = 60

    fun fuse(rankings: List<List<Int>>): Map<Int, Double> {
        val fused = HashMap<Int, Double>()
        for (ranking in rankings) {
            for ((position, item) in ranking.withIndex()) {
                fused[item] = (fused[item] ?: 0.0) + 1.0 / (K + position + 1)
            }
        }
        return fused
    }
}

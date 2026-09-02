package app.dewey.index

import java.io.DataInputStream
import java.io.InputStream
import java.text.Normalizer

/**
 * SentencePiece Unigram tokenizer, matching XLM-RoBERTa's.
 *
 * The encoder takes token ids, so this stands between every piece of text and
 * every vector the app produces. It is worth being exact about: a tokenizer that
 * is subtly wrong never crashes — it quietly produces slightly wrong vectors and
 * slightly worse search, forever. [SentencePieceTokenizerTest] therefore checks
 * it token-for-token against the reference implementation's output.
 *
 * The pipeline mirrors the reference: normalise, fold whitespace, mark word
 * boundaries with `▁`, then find the highest-scoring segmentation by Viterbi.
 */
class SentencePieceTokenizer private constructor(
    private val tokenToId: HashMap<String, Int>,
    private val scores: FloatArray,
    private val unknownId: Int,
    private val maxTokenChars: Int,
) {

    val vocabularySize: Int get() = scores.size

    /**
     * Encodes text as `<s> … </s>`, truncated to [maxTokens] including those two.
     *
     * Truncation is silent on purpose: documents are longer than any encoder's
     * window, and refusing to embed a long document would be worse than
     * embedding its beginning.
     */
    fun encode(text: String, maxTokens: Int = MAX_TOKENS): IntArray {
        require(maxTokens >= 2) { "maxTokens must leave room for the boundary tokens" }

        val prepared = prepare(text)
        if (prepared.isEmpty()) return intArrayOf(BOS_ID, EOS_ID)

        val pieces = viterbi(prepared)
        val budget = maxTokens - 2

        val ids = IntArray(minOf(pieces.size, budget) + 2)
        ids[0] = BOS_ID
        for (index in 0 until minOf(pieces.size, budget)) ids[index + 1] = pieces[index]
        ids[ids.size - 1] = EOS_ID
        return ids
    }

    /**
     * Normalisation, whitespace folding and word-boundary marking.
     *
     * NFKC stands in for SentencePiece's precompiled character map. The two agree
     * on everything this corpus contains; they can differ on rare compatibility
     * characters, which would cost a slightly different segmentation rather than
     * an error.
     */
    private fun prepare(text: String): String {
        if (text.isEmpty()) return ""

        val normalised = Normalizer.normalize(text, Normalizer.Form.NFKC)

        // Every kind of whitespace becomes a plain space, then runs collapse.
        // Tabs and newlines are whitespace to SentencePiece, not distinct
        // characters, and treating them otherwise desynchronises every token
        // that follows.
        val folded = StringBuilder(normalised.length + 1)
        var lastWasSpace = false
        for (character in normalised) {
            val isSpace = character.isWhitespace()
            if (isSpace) {
                if (!lastWasSpace) folded.append(' ')
            } else {
                folded.append(character)
            }
            lastWasSpace = isSpace
        }

        if (folded.isEmpty()) return ""

        // Word boundaries are marked, not stripped: `▁` is what distinguishes
        // "▁bank" starting a word from "bank" continuing one.
        for (index in folded.indices) {
            if (folded[index] == ' ') folded.setCharAt(index, WORD_BOUNDARY)
        }
        if (folded[0] != WORD_BOUNDARY) folded.insert(0, WORD_BOUNDARY)

        return folded.toString()
    }

    /**
     * Highest-scoring segmentation of [text] into vocabulary pieces.
     *
     * Unigram scores are log probabilities, so the best segmentation is the one
     * maximising their sum — which is a shortest-path problem over positions,
     * solved left to right in O(n · maxTokenChars).
     */
    private fun viterbi(text: String): IntArray {
        val length = text.length
        val bestScore = DoubleArray(length + 1) { Double.NEGATIVE_INFINITY }
        val bestStart = IntArray(length + 1) { -1 }
        val bestToken = IntArray(length + 1) { -1 }
        bestScore[0] = 0.0

        for (start in 0 until length) {
            if (bestScore[start] == Double.NEGATIVE_INFINITY) continue

            var matched = false
            val limit = minOf(start + maxTokenChars, length)

            for (end in start + 1..limit) {
                val id = tokenToId[text.substring(start, end)] ?: continue
                matched = true
                val candidate = bestScore[start] + scores[id]
                if (candidate > bestScore[end]) {
                    bestScore[end] = candidate
                    bestStart[end] = start
                    bestToken[end] = id
                }
            }

            // Nothing in the vocabulary starts here, so consume one character as
            // unknown. Without this a single unmapped character would strand the
            // path and lose the entire remainder of the text.
            if (!matched) {
                // Never split a surrogate pair: half a code point is not a
                // character, and downstream substring calls would throw.
                val step = if (Character.isHighSurrogate(text[start]) && start + 1 < length) 2 else 1
                val end = start + step
                val candidate = bestScore[start] + UNKNOWN_PENALTY
                if (candidate > bestScore[end]) {
                    bestScore[end] = candidate
                    bestStart[end] = start
                    bestToken[end] = unknownId
                }
            }
        }

        // Walk the backpointers. If the end was never reached — which should be
        // impossible given the unknown fallback — return nothing rather than
        // loop or throw.
        if (bestStart[length] == -1) return IntArray(0)

        val reversed = ArrayList<Int>(length / 2 + 1)
        var position = length
        while (position > 0) {
            val previous = bestStart[position]
            if (previous < 0 || previous >= position) break
            reversed.add(bestToken[position])
            position = previous
        }

        val ids = IntArray(reversed.size)
        for (index in reversed.indices) ids[index] = reversed[reversed.size - 1 - index]
        return ids
    }

    companion object {
        const val BOS_ID = 0
        const val EOS_ID = 2
        const val MAX_TOKENS = 512

        private const val WORD_BOUNDARY = '▁'
        private const val MAGIC = "DWT1"

        /**
         * Score assigned to an unmatched character. Comfortably worse than any
         * real token, so a segmentation using the vocabulary always wins, but
         * finite so the path is never abandoned.
         */
        private const val UNKNOWN_PENALTY = -20.0

        /**
         * Reads the packed vocabulary written by tools/model/prepare_assets.py.
         * See that file for the format.
         */
        fun load(input: InputStream): SentencePieceTokenizer {
            DataInputStream(input.buffered(BUFFER_BYTES)).use { stream ->
                val magic = ByteArray(4).also(stream::readFully).decodeToString()
                require(magic == MAGIC) { "Not a Dewey tokenizer file (magic '$magic')" }

                val unknownId = stream.readIntLe()
                val maxTokenBytes = stream.readIntLe()
                val count = stream.readIntLe()
                require(count in 1..MAX_VOCABULARY) { "Implausible vocabulary size $count" }

                // Sized up front: 250k entries rehashing from a default capacity
                // is several seconds of pure copying on a phone.
                val tokenToId = HashMap<String, Int>(count * 2)
                val scores = FloatArray(count)

                for (id in 0 until count) {
                    scores[id] = Float.fromBits(stream.readIntLe())
                    val length = stream.readShortLe()
                    val bytes = ByteArray(length).also(stream::readFully)
                    // Duplicates exist in the vocabulary; the reference keeps the
                    // lowest id, so do not overwrite.
                    tokenToId.putIfAbsent(bytes.decodeToString(), id)
                }

                return SentencePieceTokenizer(tokenToId, scores, unknownId, maxTokenBytes)
            }
        }

        private const val BUFFER_BYTES = 1 shl 16
        private const val MAX_VOCABULARY = 5_000_000

        // The file is little-endian; DataInputStream is not.
        private fun DataInputStream.readIntLe(): Int =
            (read() and 0xFF) or
                ((read() and 0xFF) shl 8) or
                ((read() and 0xFF) shl 16) or
                ((read() and 0xFF) shl 24)

        private fun DataInputStream.readShortLe(): Int =
            (read() and 0xFF) or ((read() and 0xFF) shl 8)
    }
}

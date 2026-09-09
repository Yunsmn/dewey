package app.dewey.classify

import app.dewey.domain.model.DocType
import app.dewey.index.Embedder
import app.dewey.index.VectorMath
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Decides what a document is, on device.
 *
 * The brief assumed this was a cloud job. It is not: every document is already
 * embedded for retrieval, and a category is just another point in that space, so
 * a label is a nearest-neighbour lookup against a handful of descriptions.
 * Measured on the test corpus that is 100% accurate, costs nothing per file,
 * works with no network, and means sorting a folder sends nothing anywhere.
 *
 * It also knows when not to answer. See [Verdict.Unsure].
 */
class DocumentClassifier(
    private val embedder: Embedder,
    private val minimumSimilarity: Float = MIN_SIMILARITY,
    private val minimumMargin: Float = MIN_MARGIN,
) {

    sealed interface Verdict {
        /**
         * @param folderName set only when the winner was a category learned
         *   from the user's own folder, in which case it — not [type] — names
         *   where the document goes. [type] is then whatever that folder's name
         *   happens to map to, often [DocType.UNKNOWN].
         */
        data class Confident(
            val type: DocType,
            val similarity: Float,
            val margin: Float,
            val folderName: String? = null,
        ) : Verdict

        /**
         * The document belongs in the review queue.
         *
         * Two different doubts, kept apart because they mean different things to
         * the user: [NOTHING_FITS] is a research paper among the bills, while
         * [TOO_CLOSE] is a document that could reasonably be two things.
         */
        data class Unsure(
            val reason: Reason,
            val bestGuess: DocType?,
            val similarity: Float,
            val margin: Float,
        ) : Verdict

        enum class Reason { NOTHING_FITS, TOO_CLOSE, NO_TEXT }
    }

    private val lock = Mutex()
    private var prototypes: List<Pair<DocType, FloatArray>>? = null

    /**
     * Embeds the category descriptions once and keeps them.
     *
     * Thirty-five short texts is about two seconds of work. Doing it per
     * document would add that to every file in a four-hundred-file sort.
     */
    private suspend fun prototypes(): List<Pair<DocType, FloatArray>> =
        prototypes ?: lock.withLock {
            prototypes ?: run {
                val entries = CategoryPrototypes.all
                val vectors = embedder.embedPassages(entries.map { it.second })
                entries.mapIndexed { index, (type, _) -> type to vectors[index] }
                    .also { prototypes = it }
            }
        }

    /**
     * @param learned categories the user taught by filing documents into folders
     *   of their own. Scored in the same pass as the built-in descriptions and
     *   against the same thresholds — see [scoreAll].
     */
    suspend fun classify(text: String, learned: List<LearnedCategory> = emptyList()): Verdict {
        if (text.isBlank()) {
            return Verdict.Unsure(Verdict.Reason.NO_TEXT, null, 0f, 0f)
        }

        // The opening of a document is what says what it is — the letterhead and
        // first paragraph. Later pages are detail, and averaging them in blurs
        // the signal that distinguishes a lease from a bill.
        val opening = text.take(OPENING_CHARS)
        return decide(embedder.embedPassages(listOf(opening)).first(), learned)
    }

    /**
     * The same decision from a vector that has already been computed.
     *
     * Sorting a folder has every document's opening embedding in hand already —
     * it was stored at import time — so re-embedding here would pay for the
     * encoder twice per document. It would also compare unlike with unlike: a
     * learned category's examples are those stored vectors, and scoring an
     * arriving document against them only means anything if it is represented
     * the same way. It is not a rounding error. On a device test two medical
     * documents that belonged in the user's own folder went to review because
     * their re-embedded opening covered more text than the stored openings they
     * were being compared against.
     */
    suspend fun decide(vector: FloatArray, learned: List<LearnedCategory> = emptyList()): Verdict {
        val ranked = scoreAll(vector, prototypes(), learned)
        if (ranked.isEmpty()) {
            return Verdict.Unsure(Verdict.Reason.NOTHING_FITS, null, 0f, 0f)
        }

        val top = ranked[0]
        val margin = if (ranked.size > 1) top.score - ranked[1].score else top.score

        return when {
            top.score < minimumSimilarity ->
                Verdict.Unsure(Verdict.Reason.NOTHING_FITS, top.type, top.score, margin)

            // A learned category winning narrowly is not a real doubt. The
            // runner-up in that case is almost always the built-in category
            // covering the same ground — "Medical" against the user's own
            // "Sante de famille" — and asking someone to choose between our
            // word for it and theirs is not a question worth asking. Theirs
            // wins. A tie between two *built-in* categories is a genuine "this
            // could be two things" and still goes to review.
            margin < minimumMargin && top.folderName == null ->
                Verdict.Unsure(Verdict.Reason.TOO_CLOSE, top.type, top.score, margin)

            else -> Verdict.Confident(top.type, top.score, margin, top.folderName)
        }
    }

    /** One candidate category and how well the document fits it. */
    internal data class Candidate(
        val type: DocType,
        val score: Float,
        /** Null for a category the app ships with. */
        val folderName: String?,
    )

    companion object {

        /**
         * Every category, built-in and learned, on one scale.
         *
         * The two are ranked together rather than consulted in turn. Asking the
         * learned ones first looked reasonable — the user's own filing should
         * outrank a sentence we wrote — and was wrong in a way a device test
         * caught: with a single learned folder there is no runner-up, so the
         * margin check never fires and every document clearing the floor falls
         * into it. A utility bill went into a folder of medical documents
         * because the built-in classifier was never asked.
         *
         * Scoring a document against real documents does read higher than
         * scoring it against a description, so the comparison is not perfectly
         * fair. Measured on that same case it does not matter: the four medical
         * documents beat their built-in category (0.86-0.96 against 0.84-0.87)
         * and the three bills lost to theirs (0.84 against 0.86). The signal is
         * bigger than the bias.
         *
         * Built-in categories take the best single description rather than a
         * mean over them: they are written in different languages, and averaging
         * a French and an Arabic one lands between both and matches neither.
         */
        internal fun scoreAll(
            vector: FloatArray,
            prototypes: List<Pair<DocType, FloatArray>>,
            learned: List<LearnedCategory>,
        ): List<Candidate> {
            val bestPerType = HashMap<DocType, Float>()
            for ((type, prototype) in prototypes) {
                val score = VectorMath.dot(vector, prototype)
                if (score > (bestPerType[type] ?: Float.NEGATIVE_INFINITY)) {
                    bestPerType[type] = score
                }
            }

            val builtIn = bestPerType.map { (type, score) -> Candidate(type, score, null) }
            val taught = learned.map { category ->
                Candidate(
                    type = DocType.UNKNOWN,
                    score = category.score(vector),
                    folderName = category.folderName,
                )
            }

            return (builtIn + taught).sortedByDescending { it.score }
        }

        /**
         * Below this, the document is not any of the categories.
         *
         * Chosen when research papers were out of scope: they topped out at
         * 0.792 against every category while corpus documents scored at least
         * 0.821 against their own, and this sat in the clean gap between.
         *
         * [DocType.PAPER] has since closed that gap on purpose — the same
         * papers now score up to 0.837, because recognising them is the point.
         * The floor is kept where it is regardless. Erring high is the whole
         * argument: a document sent for review is a mild annoyance, while one
         * confidently filed in the wrong folder costs the user's trust in the
         * feature. Measured today it sends two of sixteen real papers to review
         * — right about what they are, not quite sure enough to act.
         *
         * tools/eval/ood_check.py prints those numbers, and its header explains
         * what it can and can no longer tell you.
         */
        const val MIN_SIMILARITY = 0.80f

        /**
         * Below this, two categories fit equally well.
         *
         * Correctly classified documents average a 0.025 margin; documents that
         * belong to nothing average 0.004.
         */
        const val MIN_MARGIN = 0.008f

        /**
         * How much of a document [classify] actually looks at.
         *
         * Public because it is a read budget as much as a tuning constant: a
         * caller classifying a whole library should ask the database for this
         * many characters per document rather than for every document's full
         * text, which for four hundred scanned files is a heap the phone does
         * not have. See DocumentDao.allIndexedOpenings.
         */
        const val OPENING_CHARS = 1_600
    }
}

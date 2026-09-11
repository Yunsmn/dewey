package app.dewey.assistant

import app.dewey.cloud.AnswerComposer
import app.dewey.cloud.AnswerResult
import app.dewey.cloud.plainMessage
import app.dewey.domain.model.Document
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
import app.dewey.search.bestPassagePerDocument
import kotlinx.coroutines.CancellationException

/**
 * Answers one question about the documents the user has already indexed.
 *
 * The retrieval-and-answer pipeline both the Documents ask bar
 * ([app.dewey.ui.documents.AskViewModel]) and the full-screen assistant
 * ([app.dewey.ui.assistant.AssistantViewModel]) run: embed the question,
 * retrieve the strongest passages, hand them to [composer], and turn whatever
 * it says into an [AssistantReply]. Neither caller sees a raw [AnswerResult]
 * or an [Embedder] failure directly — this is the one place that decides what
 * "answered", "failed" and "out of questions for today" mean.
 *
 * [tryConsumeQuota] and [releaseQuota] gate the one step that actually costs
 * something — [AssistantQuota.tryConsume] and [AssistantQuota.release], as
 * bare functions for the same reason [resolveDocument] is one rather than a
 * whole repository. The spend happens right before [composer] is asked, never
 * before — a question retrieval could not even find passages for never
 * touches the budget at all — and is given back when the request turns out
 * not to have reached the model regardless: no network, nothing sent.
 *
 * Every dependency arrives as a constructor function or interface, the same
 * shape `AskViewModel` used to take these dependencies in directly before
 * this class existed, so a test can drive every branch with fakes — no ONNX
 * model, no database, no network call anywhere.
 */
class DocumentAssistant(
    private val embedderProvider: () -> Embedder,
    private val search: suspend (queryText: String, queryVector: FloatArray, limit: Int) -> List<DocumentSearch.Hit>,
    private val resolveDocument: suspend (Long) -> Document?,
    private val composer: AnswerComposer,
    private val tryConsumeQuota: suspend () -> Boolean,
    private val releaseQuota: suspend () -> Unit,
) {

    /**
     * @param onPreparing called before the embedder is asked for — see
     *   [Embedder], only slow the first time a process ever needs one.
     * @param onThinking called once retrieval has run and an answer is about
     *   to be requested — the phase most questions actually spend time in.
     */
    suspend fun ask(
        question: String,
        onPreparing: () -> Unit = {},
        onThinking: () -> Unit = {},
    ): AssistantReply = try {
        onPreparing()
        val embedder = embedderProvider()
        onThinking()
        val vector = embedder.embedQuery(question)
        val hits = search(question, vector, HIT_LIMIT)
        val passages = hitsToPassages(hits, ANSWER_PASSAGE_LIMIT)

        when {
            passages.isEmpty() -> AssistantReply.Failed(NOTHING_RELATED_MESSAGE)
            !tryConsumeQuota() -> AssistantReply.LimitReached
            else -> when (val result = answerOrGiveBack(question, passages)) {
                is AnswerResult.Answered -> AssistantReply.Answered(
                    text = result.text,
                    sources = sourceDocumentIds(result.citedDocumentIds, hits).mapNotNull { resolveDocument(it) },
                )

                is AnswerResult.Failure -> {
                    // Never reached the model — the slot just spent bought nothing.
                    if (result == AnswerResult.Failure.NoNetwork) releaseQuota()
                    AssistantReply.Failed(result.plainMessage())
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AssistantReply.Failed(COULD_NOT_ASK_MESSAGE)
    }

    /**
     * [composer]'s answer, with the quota slot given back if it throws rather
     * than returning a result: nothing came back to have paid for. A
     * cancellation keeps the slot — the person asked something else, and the
     * request they walked away from may already have been billed.
     */
    private suspend fun answerOrGiveBack(
        question: String,
        passages: List<app.dewey.cloud.RetrievedPassage>,
    ): AnswerResult =
        try {
            composer.answer(question, passages)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            releaseQuota()
            throw e
        }

    /**
     * Which documents an answer should be shown alongside.
     *
     * [cited] is [AnswerResult.Answered.citedDocumentIds] — already resolved
     * by [app.dewey.cloud.GeminiAnswerComposer] against the exact passages the
     * prompt numbered, so a non-null value is trusted as-is. `null` means the
     * model's `SOURCES:` line could not be read, and the honest fallback is
     * not "every document any retrieved passage came from" — that is the bug
     * this whole feature exists to fix — but the documents that were actually
     * strong matches: within [FALLBACK_SCORE_RATIO] of the top hit's score.
     */
    private fun sourceDocumentIds(cited: List<Long>?, hits: List<DocumentSearch.Hit>): List<Long> {
        if (cited != null) return cited

        val perDocument = bestPassagePerDocument(hits)
        val topScore = perDocument.firstOrNull()?.score ?: return emptyList()
        return perDocument.filter { it.score >= topScore * FALLBACK_SCORE_RATIO }.map { it.documentId }
    }

    companion object {
        /** Chunks considered before collapsing to one row per document. */
        private const val HIT_LIMIT = 40

        /** Matches [app.dewey.cloud.AnswerPromptBuilder.MAX_PASSAGES]. */
        internal const val ANSWER_PASSAGE_LIMIT = 8

        /**
         * How close to the top hit's score a document's best passage must be
         * to count as a source when the model's own `SOURCES:` line could not
         * be read — see [sourceDocumentIds]. Close enough to the top match to
         * plausibly be what the answer drew on, without falling back to
         * "every document retrieval touched at all".
         */
        private const val FALLBACK_SCORE_RATIO = 0.85

        const val NOTHING_RELATED_MESSAGE = "Nothing in your documents looks related to that."
        const val COULD_NOT_ASK_MESSAGE = "Couldn't ask that just now. Try again."

        /** Not `const`: it reads [AssistantQuota.DAILY_LIMIT] rather than repeating that number here. */
        val LIMIT_REACHED_MESSAGE = "You've asked ${AssistantQuota.DAILY_LIMIT} questions today. The assistant is back tomorrow."
    }
}

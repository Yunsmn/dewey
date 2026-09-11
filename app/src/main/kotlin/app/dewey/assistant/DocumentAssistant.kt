package app.dewey.assistant

import app.dewey.cloud.AnswerComposer
import app.dewey.cloud.AnswerResult
import app.dewey.domain.model.Document
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
import app.dewey.ui.documents.hitsToPassages
import app.dewey.ui.documents.plainMessage
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
            else -> when (val result = composer.answer(question, passages)) {
                is AnswerResult.Answered -> AssistantReply.Answered(
                    text = result.text,
                    sources = passages.map { it.documentId }.distinct().mapNotNull { resolveDocument(it) },
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

    companion object {
        /** Chunks considered before collapsing to one row per document. */
        private const val HIT_LIMIT = 40

        /** Matches [app.dewey.cloud.AnswerPromptBuilder.MAX_PASSAGES]. */
        internal const val ANSWER_PASSAGE_LIMIT = 8

        const val NOTHING_RELATED_MESSAGE = "Nothing in your documents looks related to that."
        const val COULD_NOT_ASK_MESSAGE = "Couldn't ask that just now. Try again."

        /** Not `const`: it reads [AssistantQuota.DAILY_LIMIT] rather than repeating that number here. */
        val LIMIT_REACHED_MESSAGE = "You've asked ${AssistantQuota.DAILY_LIMIT} questions today. The assistant is back tomorrow."
    }
}

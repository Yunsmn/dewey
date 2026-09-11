package app.dewey.assistant

import app.dewey.domain.model.Document

/**
 * What came back from asking [DocumentAssistant] a question.
 *
 * Named outcomes rather than a single generic failure, the same reasoning
 * [app.dewey.cloud.AnswerResult] gives for its own [app.dewey.cloud.AnswerResult.Failure]
 * cases: a caller showing this to someone needs to say something different for
 * "nothing found", "couldn't reach it" and "you're out of questions for today"
 * — [LimitReached] in particular has no [app.dewey.cloud.AnswerResult] to map
 * from, since the quota is spent before the model is ever asked.
 */
sealed interface AssistantReply {

    data class Answered(val text: String, val sources: List<Document>) : AssistantReply

    data class Failed(val message: String) : AssistantReply

    /** Today's cap is already spent — see [AssistantQuota]. */
    data object LimitReached : AssistantReply
}

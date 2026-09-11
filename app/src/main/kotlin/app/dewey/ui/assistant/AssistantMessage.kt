package app.dewey.ui.assistant

import app.dewey.assistant.AssistantQuota
import app.dewey.domain.model.Document

/**
 * One line in the conversation, in the order it was said.
 *
 * [Pending] and [Failure] both carry the question they answer, not just an
 * index into the list — that is what lets [AssistantViewModel.onAsk] re-ask
 * exactly the question a failed bubble belongs to, and what lets [Pending]
 * be replaced in place once the answer — or the failure, or the limit —
 * comes back for the same [id].
 */
sealed interface AssistantMessage {
    val id: Long

    data class FromUser(override val id: Long, val text: String) : AssistantMessage

    data class FromAssistant(
        override val id: Long,
        val text: String,
        val sources: List<Document> = emptyList(),
    ) : AssistantMessage

    /** A question sent and not yet answered. */
    data class Pending(override val id: Long, val question: String, val phase: Phase) : AssistantMessage {
        enum class Phase { PREPARING, THINKING }
    }

    /** [message] is already plain language — see `AnswerFailureMessages.plainMessage` and [app.dewey.assistant.DocumentAssistant]. */
    data class Failure(override val id: Long, val question: String, val message: String) : AssistantMessage

    /** Today's cap ([AssistantQuota.DAILY_LIMIT]) is spent — distinct from [Failure] because retrying buys nothing until tomorrow. */
    data class LimitReached(override val id: Long) : AssistantMessage
}

/** What the assistant screen shows: the conversation so far, and what's left of today's cap. */
data class AssistantUiState(
    val messages: List<AssistantMessage> = emptyList(),
    val remainingToday: Int = AssistantQuota.DAILY_LIMIT,
    val isSending: Boolean = false,
) {
    /** Whether asking another question right now would do anything. */
    val canSend: Boolean get() = !isSending && remainingToday > 0
}

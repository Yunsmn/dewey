package app.dewey.ui.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.assistant.AssistantReply
import app.dewey.assistant.DocumentAssistant
import app.dewey.di.AppContainer
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the full-screen assistant.
 *
 * The conversation lives only in [state] — there is nothing to persist, so a
 * process restart or navigating away and back starts fresh, and rotation
 * survives it for free because this is a [ViewModel]. Every question, whether
 * typed, retried, or tapped from a suggestion, goes through the same [ask]:
 * it appends the user's line and a [AssistantMessage.Pending] one together,
 * then replaces the pending line in place once [assistant] answers — which is
 * what lets a retry land back in the same spot in the transcript rather than
 * at the end of it.
 *
 * [remainingToday] is [AppContainer.assistantQuota]'s own flow, so this screen
 * and the Documents ask bar agree about what is left without either polling
 * the other.
 */
class AssistantViewModel(
    private val assistant: DocumentAssistant,
    remainingToday: Flow<Int>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state.asStateFlow()

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private var askJob: Job? = null
    private val nextId = AtomicLong(0)

    init {
        // Explicitly on [io], the same as every other launch here: viewModelScope's
        // own default is the Main dispatcher, which a plain JVM test never installs.
        viewModelScope.launch(io) {
            remainingToday.collect { remaining -> _state.update { it.copy(remainingToday = remaining) } }
        }
    }

    fun onInputChanged(value: String) {
        _input.value = value
    }

    /** The send button, or the field's own IME action. */
    fun onSend() {
        val question = _input.value.trim()
        if (question.isBlank()) return
        _input.value = ""
        ask(question)
    }

    /** The "Try again" action on a failed bubble, or a tapped suggestion — both just ask [question] again. */
    fun onAsk(question: String) = ask(question)

    private fun ask(question: String) {
        if (!_state.value.canSend) return

        val userId = nextId.getAndIncrement()
        val pendingId = nextId.getAndIncrement()
        _state.update {
            it.copy(
                messages = it.messages +
                    AssistantMessage.FromUser(userId, question) +
                    AssistantMessage.Pending(pendingId, question, AssistantMessage.Pending.Phase.THINKING),
                isSending = true,
            )
        }

        askJob?.cancel()
        askJob = viewModelScope.launch(io) {
            val reply = assistant.ask(
                question = question,
                onPreparing = { setPhase(pendingId, AssistantMessage.Pending.Phase.PREPARING) },
                onThinking = { setPhase(pendingId, AssistantMessage.Pending.Phase.THINKING) },
            )
            resolve(pendingId, question, reply)
        }
    }

    private fun setPhase(id: Long, phase: AssistantMessage.Pending.Phase) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message is AssistantMessage.Pending && message.id == id) message.copy(phase = phase) else message
                },
            )
        }
    }

    private fun resolve(id: Long, question: String, reply: AssistantReply) {
        _state.update { current ->
            current.copy(
                messages = current.messages.map { message ->
                    if (message.id != id) {
                        message
                    } else {
                        when (reply) {
                            is AssistantReply.Answered -> AssistantMessage.FromAssistant(id, reply.text, reply.sources)
                            is AssistantReply.Failed -> AssistantMessage.Failure(id, question, reply.message)
                            AssistantReply.LimitReached -> AssistantMessage.LimitReached(id)
                        }
                    }
                },
                isSending = false,
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AssistantViewModel(
                    assistant = container.documentAssistant,
                    remainingToday = container.assistantQuota.remainingToday,
                ) as T
        }
    }
}

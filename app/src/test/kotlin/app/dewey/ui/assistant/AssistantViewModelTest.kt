package app.dewey.ui.assistant

import app.dewey.assistant.DocumentAssistant
import app.dewey.cloud.AnswerComposer
import app.dewey.cloud.AnswerResult
import app.dewey.cloud.RetrievedPassage
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Never touches ONNX — see [Embedder]'s own note on why a fake this small is enough. */
private class FakeEmbedder : Embedder {
    override val dimensions: Int = 1
    override suspend fun embedPassages(texts: List<String>): List<FloatArray> = texts.map { floatArrayOf(0f) }
    override suspend fun embedQuery(text: String): FloatArray = floatArrayOf(0f)
}

/** A composer whose answer a test picks in advance, with no cloud call in sight. */
private class FakeComposer(
    var onAnswer: (String, List<RetrievedPassage>) -> AnswerResult = { _, _ -> AnswerResult.Answered("stub") },
) : AnswerComposer {
    override suspend fun answer(question: String, passages: List<RetrievedPassage>): AnswerResult =
        onAnswer(question, passages)
}

/**
 * [AssistantViewModel]'s conversation state machine, driven through a real
 * [DocumentAssistant] whose own dependencies are fakes — the same "no ONNX
 * model, no database, no network call" shape [app.dewey.ui.documents.AskViewModelTest]
 * uses, since [DocumentAssistant] takes every one of them as a plain function.
 */
class AssistantViewModelTest {

    private val document = Document(1, "u1", "insurance.pdf", 0, 0, docType = DocType.INSURANCE)

    private fun hit(text: String = "passage") = DocumentSearch.Hit(documentId = 1, chunkId = 0, text = text, score = 0.9)

    private fun viewModel(
        search: suspend (String, FloatArray, Int) -> List<DocumentSearch.Hit> = { _, _, _ -> listOf(hit()) },
        composer: AnswerComposer = FakeComposer(),
        tryConsumeQuota: suspend () -> Boolean = { true },
        remainingToday: MutableStateFlow<Int> = MutableStateFlow(50),
        testScheduler: TestCoroutineScheduler,
    ) = AssistantViewModel(
        assistant = DocumentAssistant(
            embedderProvider = { FakeEmbedder() },
            search = search,
            resolveDocument = { document },
            composer = composer,
            tryConsumeQuota = tryConsumeQuota,
            releaseQuota = {},
        ),
        remainingToday = remainingToday,
        io = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `starts with an empty conversation`() = runTest {
        val viewModel = viewModel(testScheduler = testScheduler)
        advanceUntilIdle()

        assertThat(viewModel.state.value.messages).isEmpty()
        assertThat(viewModel.state.value.remainingToday).isEqualTo(50)
    }

    @Test
    fun `sending a blank question does nothing`() = runTest {
        val viewModel = viewModel(testScheduler = testScheduler)
        viewModel.onInputChanged("   ")

        viewModel.onSend()
        advanceUntilIdle()

        assertThat(viewModel.state.value.messages).isEmpty()
    }

    @Test
    fun `sending clears the input and echoes the question before the answer arrives`() = runTest {
        // Retrieval is held open, so "before the answer arrives" is a state the
        // test can actually observe: on an unconfined dispatcher an ungated
        // fake answers inside onSend and the pending bubble is already gone.
        val retrieval = kotlinx.coroutines.CompletableDeferred<List<DocumentSearch.Hit>>()
        val viewModel = viewModel(search = { _, _, _ -> retrieval.await() }, testScheduler = testScheduler)
        viewModel.onInputChanged("When does my insurance renew?")

        viewModel.onSend()

        assertThat(viewModel.input.value).isEmpty()
        assertThat(viewModel.state.value.messages).hasSize(2)
        assertThat(viewModel.state.value.messages[0])
            .isEqualTo(AssistantMessage.FromUser(id = 0, text = "When does my insurance renew?"))
        assertThat(viewModel.state.value.messages[1]).isInstanceOf(AssistantMessage.Pending::class.java)
        assertThat(viewModel.state.value.isSending).isTrue()

        retrieval.complete(listOf(hit()))
        advanceUntilIdle()
        assertThat(viewModel.state.value.isSending).isFalse()
    }

    @Test
    fun `an answered question replaces the pending bubble in place, with its sources`() = runTest {
        val composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("Renews in March.") })
        val viewModel = viewModel(composer = composer, testScheduler = testScheduler)
        viewModel.onInputChanged("insurance")

        viewModel.onSend()
        advanceUntilIdle()

        val messages = viewModel.state.value.messages
        assertThat(messages).hasSize(2)
        assertThat(messages[1]).isEqualTo(AssistantMessage.FromAssistant(id = 1, text = "Renews in March.", sources = listOf(document)))
        assertThat(viewModel.state.value.isSending).isFalse()
    }

    @Test
    fun `no passages retrieved becomes a failure bubble that can retry the same question`() = runTest {
        val viewModel = viewModel(search = { _, _, _ -> emptyList() }, testScheduler = testScheduler)
        viewModel.onInputChanged("something obscure")

        viewModel.onSend()
        advanceUntilIdle()

        val failure = viewModel.state.value.messages[1] as AssistantMessage.Failure
        assertThat(failure.question).isEqualTo("something obscure")
        assertThat(failure.message).isEqualTo(DocumentAssistant.NOTHING_RELATED_MESSAGE)
    }

    @Test
    fun `retrying a failed question asks it again`() = runTest {
        var answerCalls = 0
        val composer = FakeComposer(onAnswer = { _, _ -> answerCalls++; AnswerResult.Failure.NoNetwork })
        val viewModel = viewModel(composer = composer, testScheduler = testScheduler)
        viewModel.onInputChanged("insurance")
        viewModel.onSend()
        advanceUntilIdle()
        val failure = viewModel.state.value.messages.last() as AssistantMessage.Failure

        viewModel.onAsk(failure.question)
        advanceUntilIdle()

        assertThat(answerCalls).isEqualTo(2)
        assertThat(viewModel.state.value.messages).hasSize(4)
    }

    @Test
    fun `reaching the daily limit becomes its own bubble, and disables sending`() = runTest {
        val remaining = MutableStateFlow(50)
        val viewModel = viewModel(tryConsumeQuota = { false }, remainingToday = remaining, testScheduler = testScheduler)
        viewModel.onInputChanged("insurance")

        viewModel.onSend()
        advanceUntilIdle()

        assertThat(viewModel.state.value.messages[1]).isEqualTo(AssistantMessage.LimitReached(id = 1))

        remaining.value = 0
        assertThat(viewModel.state.value.canSend).isFalse()
    }

    @Test
    fun `a suggestion tapped from the empty state asks it like any other question`() = runTest {
        val composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("Soon.") })
        val viewModel = viewModel(composer = composer, testScheduler = testScheduler)

        viewModel.onAsk("Which bills are due soon?")
        advanceUntilIdle()

        assertThat(viewModel.state.value.messages[0]).isEqualTo(AssistantMessage.FromUser(id = 0, text = "Which bills are due soon?"))
        assertThat(viewModel.state.value.messages[1]).isEqualTo(AssistantMessage.FromAssistant(id = 1, text = "Soon.", sources = listOf(document)))
    }
}

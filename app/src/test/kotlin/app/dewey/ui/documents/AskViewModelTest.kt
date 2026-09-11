package app.dewey.ui.documents

import app.dewey.assistant.DocumentAssistant
import app.dewey.cloud.AnswerComposer
import app.dewey.cloud.AnswerResult
import app.dewey.cloud.RetrievedPassage
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
import app.dewey.search.SearchUiState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * A stand-in encoder that never touches ONNX — see [AskViewModel]'s own
 * [Embedder] parameter for why a test can supply one this small.
 */
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
 * [AskViewModel]'s two independent flows — search-as-you-type and the
 * deliberate ask — driven entirely through fakes, no database or network call
 * anywhere. [io] is [UnconfinedTestDispatcher] tied to `runTest`'s own
 * scheduler so [kotlinx.coroutines.delay] inside the debounce advances with
 * [advanceUntilIdle] instead of a real wait.
 */
class AskViewModelTest {

    private val document = Document(1, "u1", "insurance.pdf", 0, 0, docType = DocType.INSURANCE)

    private fun hit(documentId: Long = 1, text: String = "passage", score: Double = 0.9) =
        DocumentSearch.Hit(documentId, chunkId = 0, text = text, score = score)

    private fun viewModel(
        embedder: Embedder = FakeEmbedder(),
        search: suspend (String, FloatArray, Int) -> List<DocumentSearch.Hit> = { _, _, _ -> listOf(hit()) },
        resolveDocument: suspend (Long) -> Document? = { document },
        composer: AnswerComposer = FakeComposer(),
        testScheduler: TestCoroutineScheduler,
    ) = AskViewModel(
        assistant = DocumentAssistant(
            embedderProvider = { embedder },
            search = search,
            resolveDocument = resolveDocument,
            composer = composer,
            tryConsumeQuota = { true },
            releaseQuota = {},
        ),
        embedderProvider = { embedder },
        search = search,
        resolveDocument = resolveDocument,
        io = UnconfinedTestDispatcher(testScheduler),
    )

    @Test
    fun `starts out asking nothing`() = runTest {
        val viewModel = viewModel(testScheduler = testScheduler)

        assertThat(viewModel.query.value).isEmpty()
        assertThat(viewModel.searchState.value).isEqualTo(SearchUiState.Idle)
        assertThat(viewModel.answerState.value).isEqualTo(AnswerUiState.Idle)
    }

    @Test
    fun `typing something eventually shows the matching documents`() = runTest {
        val viewModel = viewModel(testScheduler = testScheduler)

        viewModel.onQueryChanged("insurance")
        advanceUntilIdle()

        val state = viewModel.searchState.value
        assertThat(state).isInstanceOf(SearchUiState.Results::class.java)
        assertThat((state as SearchUiState.Results).results.map { it.document }).containsExactly(document)
    }

    @Test
    fun `clearing the field clears both the results and the answer`() = runTest {
        val viewModel = viewModel(testScheduler = testScheduler)
        viewModel.onQueryChanged("insurance")
        advanceUntilIdle()
        viewModel.onAsk()
        advanceUntilIdle()

        viewModel.onQueryChanged("")

        assertThat(viewModel.searchState.value).isEqualTo(SearchUiState.Idle)
        assertThat(viewModel.answerState.value).isEqualTo(AnswerUiState.Idle)
    }

    @Test
    fun `asking a blank question does nothing`() = runTest {
        val viewModel = viewModel(testScheduler = testScheduler)

        viewModel.onAsk()
        advanceUntilIdle()

        assertThat(viewModel.answerState.value).isEqualTo(AnswerUiState.Idle)
    }

    @Test
    fun `a composed answer carries its sources`() = runTest {
        val composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("Renews in March.") })
        val viewModel = viewModel(composer = composer, testScheduler = testScheduler)
        viewModel.onQueryChanged("insurance")

        viewModel.onAsk()
        advanceUntilIdle()

        assertThat(viewModel.answerState.value)
            .isEqualTo(AnswerUiState.Answered(text = "Renews in March.", sources = listOf(document)))
    }

    @Test
    fun `no passages retrieved reads as a plain message, not an empty answer`() = runTest {
        val viewModel = viewModel(search = { _, _, _ -> emptyList() }, testScheduler = testScheduler)
        viewModel.onQueryChanged("something obscure")

        viewModel.onAsk()
        advanceUntilIdle()

        assertThat(viewModel.answerState.value)
            .isEqualTo(AnswerUiState.Failed(AskViewModel.NOTHING_RELATED_MESSAGE))
    }

    @Test
    fun `a composer failure reaches the screen as its own plain message`() = runTest {
        val composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Failure.NoNetwork })
        val viewModel = viewModel(composer = composer, testScheduler = testScheduler)
        viewModel.onQueryChanged("insurance")

        viewModel.onAsk()
        advanceUntilIdle()

        assertThat(viewModel.answerState.value)
            .isEqualTo(AnswerUiState.Failed(AnswerResult.Failure.NoNetwork.plainMessage()))
    }

    @Test
    fun `an unexpected exception while asking is reported, not left to crash`() = runTest {
        val viewModel = viewModel(
            search = { _, _, _ -> throw IllegalStateException("boom") },
            testScheduler = testScheduler,
        )
        viewModel.onQueryChanged("insurance")

        viewModel.onAsk()
        advanceUntilIdle()

        assertThat(viewModel.answerState.value).isEqualTo(AnswerUiState.Failed(AskViewModel.COULD_NOT_ASK_MESSAGE))
    }

    @Test
    fun `sources list a document once even when several of its passages were retrieved`() = runTest {
        var resolveCalls = 0
        val viewModel = viewModel(
            search = { _, _, _ -> listOf(hit(documentId = 1, text = "a"), hit(documentId = 1, text = "b")) },
            resolveDocument = { resolveCalls++; document },
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("answer") }),
            testScheduler = testScheduler,
        )
        viewModel.onQueryChanged("insurance")

        viewModel.onAsk()
        advanceUntilIdle()

        val state = viewModel.answerState.value as AnswerUiState.Answered
        assertThat(state.sources).containsExactly(document)
        assertThat(resolveCalls).isEqualTo(1)
    }
}

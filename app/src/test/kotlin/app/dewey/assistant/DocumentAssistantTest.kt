package app.dewey.assistant

import app.dewey.cloud.AnswerComposer
import app.dewey.cloud.AnswerResult
import app.dewey.cloud.RetrievedPassage
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
import app.dewey.ui.documents.plainMessage
import com.google.common.truth.Truth.assertThat
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
 * [DocumentAssistant.ask] driven entirely through fakes — no ONNX model, no
 * database, no network call and no real [AssistantQuota] anywhere. The quota
 * itself is exercised separately in [AssistantQuotaTest]; here it is two
 * counters, so a test can assert exactly when a question was actually
 * charged for.
 */
class DocumentAssistantTest {

    private val document = Document(1, "u1", "insurance.pdf", 0, 0, docType = DocType.INSURANCE)

    private fun hit(documentId: Long = 1, text: String = "passage") =
        DocumentSearch.Hit(documentId, chunkId = 0, text = text, score = 0.9)

    private fun assistant(
        embedder: Embedder = FakeEmbedder(),
        search: suspend (String, FloatArray, Int) -> List<DocumentSearch.Hit> = { _, _, _ -> listOf(hit()) },
        resolveDocument: suspend (Long) -> Document? = { document },
        composer: AnswerComposer = FakeComposer(),
        tryConsumeQuota: suspend () -> Boolean = { true },
        releaseQuota: suspend () -> Unit = {},
    ) = DocumentAssistant(
        embedderProvider = { embedder },
        search = search,
        resolveDocument = resolveDocument,
        composer = composer,
        tryConsumeQuota = tryConsumeQuota,
        releaseQuota = releaseQuota,
    )

    @Test
    fun `an answered question carries its sources and spends one question of quota`() = runTest {
        var consumeCalls = 0
        val instance = assistant(
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("Renews in March.") }),
            tryConsumeQuota = { consumeCalls++; true },
        )

        val reply = instance.ask("When does my insurance renew?")

        assertThat(reply).isEqualTo(AssistantReply.Answered(text = "Renews in March.", sources = listOf(document)))
        assertThat(consumeCalls).isEqualTo(1)
    }

    @Test
    fun `no passages retrieved is a plain failure and never touches the quota`() = runTest {
        var consumeCalls = 0
        val instance = assistant(search = { _, _, _ -> emptyList() }, tryConsumeQuota = { consumeCalls++; true })

        val reply = instance.ask("something obscure")

        assertThat(reply).isEqualTo(AssistantReply.Failed(DocumentAssistant.NOTHING_RELATED_MESSAGE))
        assertThat(consumeCalls).isEqualTo(0)
    }

    @Test
    fun `a composer failure reaches the caller as its own plain message, and still spent a question`() = runTest {
        var consumeCalls = 0
        val instance = assistant(
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Failure.TimedOut }),
            tryConsumeQuota = { consumeCalls++; true },
        )

        val reply = instance.ask("insurance")

        assertThat(reply).isEqualTo(AssistantReply.Failed(AnswerResult.Failure.TimedOut.plainMessage()))
        assertThat(consumeCalls).isEqualTo(1)
    }

    @Test
    fun `the daily limit reaches the caller as its own outcome, with the composer never asked`() = runTest {
        var composerCalls = 0
        val instance = assistant(
            composer = FakeComposer(onAnswer = { _, _ -> composerCalls++; AnswerResult.Answered("unused") }),
            tryConsumeQuota = { false },
        )

        val reply = instance.ask("insurance")

        assertThat(reply).isEqualTo(AssistantReply.LimitReached)
        assertThat(composerCalls).isEqualTo(0)
    }

    @Test
    fun `a question the device could not send is not counted — the spent slot is given back`() = runTest {
        var consumeCalls = 0
        var releaseCalls = 0
        val instance = assistant(
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Failure.NoNetwork }),
            tryConsumeQuota = { consumeCalls++; true },
            releaseQuota = { releaseCalls++ },
        )

        val reply = instance.ask("insurance")

        assertThat(reply).isEqualTo(AssistantReply.Failed(AnswerResult.Failure.NoNetwork.plainMessage()))
        assertThat(consumeCalls).isEqualTo(1)
        assertThat(releaseCalls).isEqualTo(1)
    }

    @Test
    fun `an unexpected exception while asking is reported, not left to crash`() = runTest {
        val instance = assistant(search = { _, _, _ -> throw IllegalStateException("boom") })

        val reply = instance.ask("insurance")

        assertThat(reply).isEqualTo(AssistantReply.Failed(DocumentAssistant.COULD_NOT_ASK_MESSAGE))
    }

    @Test
    fun `sources list a document once even when several of its passages were retrieved`() = runTest {
        var resolveCalls = 0
        val instance = assistant(
            search = { _, _, _ -> listOf(hit(documentId = 1, text = "a"), hit(documentId = 1, text = "b")) },
            resolveDocument = { resolveCalls++; document },
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("answer") }),
        )

        val reply = instance.ask("insurance") as AssistantReply.Answered

        assertThat(reply.sources).containsExactly(document)
        assertThat(resolveCalls).isEqualTo(1)
    }
}

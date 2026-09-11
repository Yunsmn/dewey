package app.dewey.assistant

import app.dewey.cloud.AnswerComposer
import app.dewey.cloud.AnswerResult
import app.dewey.cloud.RetrievedPassage
import app.dewey.cloud.plainMessage
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
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

    private fun hit(documentId: Long = 1, text: String = "passage", score: Double = 0.9) =
        DocumentSearch.Hit(documentId, chunkId = 0, text = text, score = score)

    private fun document(id: Long) = Document(id, "u1", "doc-$id.pdf", 0, 0, docType = DocType.INSURANCE)

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
    fun `a composer that throws gives the question back and replies with a plain failure`() = runTest {
        var consumeCalls = 0
        var releaseCalls = 0
        val instance = assistant(
            composer = FakeComposer(onAnswer = { _, _ -> throw IllegalStateException("sdk blew up") }),
            tryConsumeQuota = { consumeCalls++; true },
            releaseQuota = { releaseCalls++ },
        )

        val reply = instance.ask("When does my insurance renew?")

        assertThat(reply).isEqualTo(AssistantReply.Failed(DocumentAssistant.COULD_NOT_ASK_MESSAGE))
        assertThat(consumeCalls).isEqualTo(1)
        assertThat(releaseCalls).isEqualTo(1)
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

    @Test
    fun `sources are exactly the documents the answer cited, in citation order`() = runTest {
        val documents = (1L..3L).associateWith { document(it) }
        val instance = assistant(
            search = { _, _, _ -> listOf(hit(1, "a"), hit(2, "b"), hit(3, "c")) },
            resolveDocument = { id -> documents[id] },
            // "Which bills are due soon?" only drew on passages 3 and 1 — the
            // whole point of citing rather than listing every retrieved
            // document.
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("Two bills.", citedDocumentIds = listOf(3L, 1L)) }),
        )

        val reply = instance.ask("Which bills are due soon?") as AssistantReply.Answered

        assertThat(reply.sources).containsExactly(document(3), document(1)).inOrder()
    }

    @Test
    fun `SOURCES colon none means no sources, not a guess`() = runTest {
        var resolveCalls = 0
        val instance = assistant(
            search = { _, _, _ -> listOf(hit(1, "a")) },
            resolveDocument = { resolveCalls++; document },
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("Not in your documents.", citedDocumentIds = emptyList()) }),
        )

        val reply = instance.ask("insurance") as AssistantReply.Answered

        assertThat(reply.sources).isEmpty()
        assertThat(resolveCalls).isEqualTo(0)
    }

    @Test
    fun `an unparseable SOURCES line falls back to documents scoring close to the top hit`() = runTest {
        val documents = (1L..3L).associateWith { document(it) }
        val instance = assistant(
            search = { _, _, _ ->
                listOf(
                    hit(1, "strong match", score = 1.0),
                    hit(2, "close match", score = 0.9), // within 85% of the top score
                    hit(3, "weak match", score = 0.5), // well below it
                )
            },
            resolveDocument = { id -> documents[id] },
            // citedDocumentIds left at its default (null): the model's
            // SOURCES line was missing or unparseable.
            composer = FakeComposer(onAnswer = { _, _ -> AnswerResult.Answered("An answer.") }),
        )

        val reply = instance.ask("insurance") as AssistantReply.Answered

        assertThat(reply.sources).containsExactly(document(1), document(2))
    }
}

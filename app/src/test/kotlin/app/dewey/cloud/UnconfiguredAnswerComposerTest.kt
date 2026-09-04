package app.dewey.cloud

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * This is what the free tier runs with — no Firebase project, no network
 * call, no exception. It has to answer every call the same deterministic way.
 */
class UnconfiguredAnswerComposerTest {

    @Test
    fun `reports not configured rather than throwing or answering`() = runTest {
        val result = UnconfiguredAnswerComposer().answer("anything", emptyList())

        assertThat(result).isEqualTo(AnswerResult.Failure.NotConfigured)
    }

    @Test
    fun `ignores passages entirely — nothing to send when there is nowhere to send it`() = runTest {
        val passages = listOf(RetrievedPassage(1, "a document's contents"))

        val result = UnconfiguredAnswerComposer().answer("question", passages)

        assertThat(result).isEqualTo(AnswerResult.Failure.NotConfigured)
    }
}

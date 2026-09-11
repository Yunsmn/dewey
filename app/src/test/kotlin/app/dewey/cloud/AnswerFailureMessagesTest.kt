package app.dewey.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Every [AnswerResult.Failure] must turn into a sentence with no enum name, no
 * stack trace, and no jargon in it — see [plainMessage]'s own KDoc for why
 * that is the whole point of the function.
 */
class AnswerFailureMessagesTest {

    @Test
    fun `every failure reads as a plain sentence, not a technical term`() {
        val technicalWords = listOf(
            "NotConfigured", "NoNetwork", "TimedOut", "QuotaExceeded", "NotAuthorized",
            "Blocked", "EmptyResponse", "Unavailable", "exception", "null",
        )

        val failures: List<AnswerResult.Failure> = listOf(
            AnswerResult.Failure.NotConfigured,
            AnswerResult.Failure.NoNetwork,
            AnswerResult.Failure.TimedOut,
            AnswerResult.Failure.QuotaExceeded,
            AnswerResult.Failure.NotAuthorized,
            AnswerResult.Failure.Blocked("safety"),
            AnswerResult.Failure.EmptyResponse,
            AnswerResult.Failure.Unavailable("500 from backend"),
        )

        for (failure in failures) {
            val message = failure.plainMessage()
            assertThat(message).isNotEmpty()
            for (word in technicalWords) {
                assertThat(message).doesNotContain(word)
            }
        }
    }

    @Test
    fun `no network specifically mentions the connection`() {
        assertThat(AnswerResult.Failure.NoNetwork.plainMessage()).contains("connection")
    }

    @Test
    fun `quota exceeded reads as a try-later, not a dead end`() {
        assertThat(AnswerResult.Failure.QuotaExceeded.plainMessage()).contains("later")
    }
}

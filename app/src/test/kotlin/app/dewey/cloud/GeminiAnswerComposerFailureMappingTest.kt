package app.dewey.cloud

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * How a cloud failure becomes something the user can read.
 *
 * Driven through [classifyFailure] rather than by constructing the SDK's own
 * exceptions: those constructors are `internal` to the Firebase module and
 * cannot be called from here. That is invisible in decompiled bytecode, where
 * Kotlin's `internal` looks public — which is exactly how the first version of
 * this test came to be written against constructors that do not exist for us.
 *
 * What matters is that every failure reaches a distinct, honest state. A cloud
 * call that quietly returns nothing is worse than one that says why.
 */
class GeminiAnswerComposerFailureMappingTest {

    @Test
    fun `quota exhaustion is reported as quota, not a generic failure`() {
        val result = classifyFailure(FirebaseFailureKind.QUOTA_EXCEEDED, "429", null)

        assertThat(result).isEqualTo(AnswerResult.Failure.QuotaExceeded)
    }

    @Test
    fun `a timeout is its own state`() {
        assertThat(classifyFailure(FirebaseFailureKind.TIMED_OUT, null, null))
            .isEqualTo(AnswerResult.Failure.TimedOut)
    }

    @Test
    fun `a missing permission maps to not authorised`() {
        // This is what an App Check rejection looks like from the client side,
        // so it must not be confused with being offline.
        assertThat(classifyFailure(FirebaseFailureKind.PERMISSION_MISSING, "denied", null))
            .isEqualTo(AnswerResult.Failure.NotAuthorized)
    }

    @Test
    fun `a blocked prompt keeps its reason`() {
        val result = classifyFailure(FirebaseFailureKind.PROMPT_BLOCKED, "SAFETY", null)

        assertThat(result).isInstanceOf(AnswerResult.Failure.Blocked::class.java)
        assertThat((result as AnswerResult.Failure.Blocked).reason).isEqualTo("SAFETY")
    }

    @Test
    fun `a stopped response is blocked with a reason too`() {
        val result = classifyFailure(FirebaseFailureKind.RESPONSE_STOPPED, "RECITATION", null)

        assertThat((result as AnswerResult.Failure.Blocked).reason).isEqualTo("RECITATION")
    }

    @Test
    fun `blocked without a reason still says something`() {
        val prompt = classifyFailure(FirebaseFailureKind.PROMPT_BLOCKED, null, null)
        val stopped = classifyFailure(FirebaseFailureKind.RESPONSE_STOPPED, null, null)

        assertThat((prompt as AnswerResult.Failure.Blocked).reason).isNotEmpty()
        assertThat((stopped as AnswerResult.Failure.Blocked).reason).isNotEmpty()
        // The two must not collapse into the same message: they are different
        // problems and the second is not the user's fault.
        assertThat(prompt.reason).isNotEqualTo(stopped.reason)
    }

    @Test
    fun `an IO cause means we never reached the backend`() {
        val result = classifyFailure(FirebaseFailureKind.UNKNOWN, "failed", IOException("no route to host"))

        assertThat(result).isEqualTo(AnswerResult.Failure.NoNetwork)
    }

    @Test
    fun `a socket timeout also counts as offline`() {
        // SocketTimeoutException extends IOException; the branch must catch
        // subclasses rather than only the exact type.
        val result = classifyFailure(FirebaseFailureKind.UNKNOWN, "failed", SocketTimeoutException("timed out"))

        assertThat(result).isEqualTo(AnswerResult.Failure.NoNetwork)
    }

    @Test
    fun `an unknown failure without an IO cause is unavailable, not offline`() {
        val result = classifyFailure(FirebaseFailureKind.UNKNOWN, "backend said no", IllegalStateException())

        assertThat(result).isInstanceOf(AnswerResult.Failure.Unavailable::class.java)
        assertThat((result as AnswerResult.Failure.Unavailable).reason).isEqualTo("backend said no")
    }

    @Test
    fun `an unrecognised exception type still produces a usable message`() {
        val result = classifyFailure(FirebaseFailureKind.UNRECOGNISED, null, null)

        assertThat((result as AnswerResult.Failure.Unavailable).reason).isNotEmpty()
    }

    @Test
    fun `every failure kind maps to something`() {
        // A kind added later without a branch here would otherwise fail only in
        // production, on whichever error is rarest.
        for (kind in FirebaseFailureKind.entries) {
            assertThat(classifyFailure(kind, "m", null)).isNotNull()
        }
    }
}

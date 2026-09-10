package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.ui.tools.ToolRunState
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class ProtectLogicTest {

    private val target = mockk<Uri>(relaxed = true)

    // -- canRunProtect ------------------------------------------------------

    @Test
    fun `needs a source, a password meeting the policy, and a match`() {
        assertThat(canRunProtect(hasSource = false, password = "longenough1", confirmPassword = "longenough1"))
            .isFalse()
        assertThat(canRunProtect(hasSource = true, password = "short", confirmPassword = "short")).isFalse()
        assertThat(canRunProtect(hasSource = true, password = "longenough1", confirmPassword = "different1"))
            .isFalse()
        assertThat(canRunProtect(hasSource = true, password = "longenough1", confirmPassword = "longenough1"))
            .isTrue()
    }

    // -- protectPasswordReason ------------------------------------------------

    @Test
    fun `an untouched password field shows no reason yet`() {
        assertThat(protectPasswordReason("")).isNull()
    }

    @Test
    fun `a short password names the minimum length`() {
        assertThat(protectPasswordReason("short")).isEqualTo("Use at least 8 characters.")
    }

    @Test
    fun `a password meeting the policy shows no reason`() {
        assertThat(protectPasswordReason("longenough1")).isNull()
    }

    // -- protectConfirmReason -------------------------------------------------

    @Test
    fun `a blank confirm field is not nagged`() {
        assertThat(protectConfirmReason("abcdefgh", "")).isNull()
    }

    @Test
    fun `a mismatched confirm is named`() {
        assertThat(protectConfirmReason("abcdefgh", "abcdefg9")).isEqualTo("Passwords don't match.")
    }

    @Test
    fun `a matching confirm shows no reason`() {
        assertThat(protectConfirmReason("abcdefgh", "abcdefgh")).isNull()
    }

    // -- protectSummary --------------------------------------------------------

    @Test
    fun `the summary says printing is still possible when it was allowed`() {
        assertThat(protectSummary(allowPrinting = true)).contains("can still be printed")
    }

    @Test
    fun `the summary asks that printing not happen when it was refused`() {
        assertThat(protectSummary(allowPrinting = false)).contains("not be printed")
    }

    // -- afterRun ---------------------------------------------------------------

    @Test
    fun `a successful protect clears both password fields`() {
        val state = ProtectUiState(password = "longenough1", confirmPassword = "longenough1")

        val after = state.afterRun(Result.success(Unit), target)

        assertThat(after.password).isEmpty()
        assertThat(after.confirmPassword).isEmpty()
        assertThat(after.run).isEqualTo(ToolRunState.Done(protectSummary(state.allowPrinting), target))
    }

    @Test
    fun `a failed protect keeps what was typed so it can be fixed`() {
        val state = ProtectUiState(password = "short", confirmPassword = "short")

        val after = state.afterRun(Result.failure(RuntimeException("boom")), target)

        assertThat(after.password).isEqualTo("short")
        assertThat(after.confirmPassword).isEqualTo("short")
        assertThat(after.run).isEqualTo(ToolRunState.Failed("Something went wrong. Nothing was changed."))
    }
}

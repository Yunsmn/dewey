package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.pdf.ProtectFailure
import app.dewey.pdf.ProtectToolException
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class UnlockLogicTest {

    private val target = mockk<Uri>(relaxed = true)

    @Test
    fun `needs a source and a non-empty password`() {
        assertThat(canRunUnlock(hasSource = false, password = "anything")).isFalse()
        assertThat(canRunUnlock(hasSource = true, password = "")).isFalse()
        assertThat(canRunUnlock(hasSource = true, password = "x")).isTrue()
    }

    @Test
    fun `a successful unlock clears the password and reports it plainly`() {
        val state = UnlockUiState(password = "hunter2")

        val after = state.afterRun(Result.success(Unit), target)

        assertThat(after.password).isEmpty()
        assertThat(after.run).isEqualTo(ToolRunState.Done(unlockSummary(), target))
    }

    @Test
    fun `a wrong password is named plainly and the password is kept for a retry`() {
        val state = UnlockUiState(password = "guess")

        val after = state.afterRun(Result.failure(ProtectToolException(ProtectFailure.WrongPassword)), target)

        assertThat(after.password).isEqualTo("guess")
        assertThat(after.run).isEqualTo(ToolRunState.Failed("That password doesn't open this PDF."))
    }

    @Test
    fun `the picked file is kept on failure too, since a retry needs it`() {
        val file = PickedFile(mockk<Uri>(relaxed = true), "statement.pdf", 1_000)
        val state = UnlockUiState(source = file, password = "guess")

        val after = state.afterRun(Result.failure(ProtectToolException(ProtectFailure.WrongPassword)), target)

        assertThat(after.source).isEqualTo(file)
    }
}

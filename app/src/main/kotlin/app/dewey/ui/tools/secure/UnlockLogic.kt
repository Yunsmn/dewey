package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.toolFailureMessage

/** Whether an unlock run has a document and something typed to try against it. */
fun canRunUnlock(hasSource: Boolean, password: String): Boolean = hasSource && password.isNotEmpty()

/** What to tell the user after removing a PDF's password. */
fun unlockSummary(): String = "Unlocked. The password is no longer needed for this copy."

/**
 * Where an unlock run lands.
 *
 * A right password is done with the moment it has opened the file, so it is
 * cleared on success like [ProtectUiState]'s. A wrong one is the opposite
 * case from every other tool's failure: the password itself is the only thing
 * that needs to change before trying again, so it — and the file, already
 * untouched by a failed run — is kept rather than wiped, so a retry is a
 * retyped password, not a whole form filled in again.
 */
fun UnlockUiState.afterRun(result: Result<Unit>, target: Uri): UnlockUiState = result.fold(
    onSuccess = { copy(password = "", run = ToolRunState.Done(unlockSummary(), target)) },
    onFailure = { copy(run = ToolRunState.Failed(toolFailureMessage(it))) },
)

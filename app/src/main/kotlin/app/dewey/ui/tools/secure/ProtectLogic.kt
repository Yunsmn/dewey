package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.pdf.PasswordCheck
import app.dewey.pdf.ProtectPasswordPolicy
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.toolFailureMessage

/** Whether a protect run has everything it needs to encrypt the document. */
fun canRunProtect(hasSource: Boolean, password: String, confirmPassword: String): Boolean =
    hasSource && ProtectPasswordPolicy.isValid(password) && password == confirmPassword

/**
 * The reason shown under the password field as the user types, or null when
 * there is nothing to say.
 *
 * Silent on an empty field rather than opening with "Enter a password.": that
 * message is right the moment a run is attempted, but shown before anyone has
 * typed anything it reads as the field nagging at first sight.
 */
fun protectPasswordReason(password: String): String? {
    if (password.isEmpty()) return null
    return when (val check = ProtectPasswordPolicy.check(password)) {
        PasswordCheck.Valid, PasswordCheck.Empty -> null
        is PasswordCheck.TooShort -> "Use at least ${check.minimumLength} characters."
        is PasswordCheck.TooLong -> "That's too long — keep it under ${check.maximumLength} characters."
    }
}

/** The reason shown under the confirm field, or null while it is blank or matches. */
fun protectConfirmReason(password: String, confirmPassword: String): String? =
    if (confirmPassword.isNotEmpty() && password != confirmPassword) "Passwords don't match." else null

/**
 * What to tell the user after protecting a PDF.
 *
 * Said honestly rather than as a blanket "Protected": see [app.dewey.pdf.protect]'s
 * own doc for why permission bits like "no printing" are a request a compliant
 * reader can choose to honour, not an enforced restriction, and why the copy
 * here never claims otherwise.
 */
fun protectSummary(allowPrinting: Boolean): String = if (allowPrinting) {
    "Protected with a password. The file can still be printed."
} else {
    "Protected with a password, with a request that it not be printed — not every app honors that."
}

/**
 * Where a protect run lands: the password fields are cleared on success, since
 * the run is the last thing that needed them — see the class doc on
 * [ProtectViewModel] for why they are kept on failure instead of wiped along
 * with everything else.
 */
fun ProtectUiState.afterRun(result: Result<Unit>, target: Uri): ProtectUiState = result.fold(
    onSuccess = { copy(password = "", confirmPassword = "", run = ToolRunState.Done(protectSummary(allowPrinting), target)) },
    onFailure = { copy(run = ToolRunState.Failed(toolFailureMessage(it))) },
)

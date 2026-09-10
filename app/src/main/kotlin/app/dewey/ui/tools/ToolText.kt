package app.dewey.ui.tools

import app.dewey.pdf.PageOperationException
import app.dewey.pdf.PageOperations
import app.dewey.pdf.PasswordCheck
import app.dewey.pdf.PdfToolException
import app.dewey.pdf.PdfWorkspace
import app.dewey.pdf.ProtectFailure
import app.dewey.pdf.ProtectToolException
import app.dewey.pdf.RasterFailure
import app.dewey.pdf.RasterToolException

/**
 * The sentence a tool screen shows when a run fails.
 *
 * The engines name their failures precisely — too large, encrypted, a page
 * that does not exist — and this turns each into something a person can act
 * on. Raw exception messages are never shown: they are written for a log, and
 * they can carry file paths and provider internals that do not belong on a
 * screen.
 */
fun toolFailureMessage(error: Throwable): String = when (error) {
    is PdfToolException -> when (error.failure) {
        is PdfWorkspace.Failure.TooLarge -> "This is too large to edit on the phone."
        is PdfWorkspace.Failure.Unreadable -> "This file couldn't be opened as a PDF."
        is PdfWorkspace.Failure.Encrypted -> "This PDF is password-protected. Unlock it first."
        is PdfWorkspace.Failure.CouldNotWrite -> "The result couldn't be saved there. Try another location."
    }

    is RasterToolException -> when (error.failure) {
        is RasterFailure.TooLarge -> "This is too large to convert on the phone."
        is RasterFailure.Unreadable -> "This file couldn't be opened as a PDF."
        is RasterFailure.Protected -> "This PDF is password-protected. Unlock it first."
        is RasterFailure.WriteFailed -> "The result couldn't be saved there. Try another location."
    }

    is PageOperationException -> when (val issue = error.issue) {
        is PageOperations.Issue.EmptyRange -> "Enter the pages to use, like 1-3, 7."
        is PageOperations.Issue.MalformedToken -> "\"${issue.token}\" isn't a page number or a range."
        is PageOperations.Issue.PageOutOfRange ->
            "There is no page ${issue.page} — this document has ${pageWord(issue.pageCount)}."
        is PageOperations.Issue.WouldEmptyDocument -> "That would remove every page."
        is PageOperations.Issue.InvalidRotation -> "Rotation has to be a quarter turn."
        is PageOperations.Issue.TooFewDocuments -> "Choose at least two PDFs to merge."
    }

    is ProtectToolException -> when (val failure = error.failure) {
        is ProtectFailure.WeakPassword -> when (val check = failure.reason) {
            PasswordCheck.Empty -> "Enter a password."
            is PasswordCheck.TooShort -> "Use at least ${check.minimumLength} characters."
            else -> "Choose a different password."
        }
        ProtectFailure.WrongPassword -> "That password doesn't open this PDF."
    }

    else -> "Something went wrong. Nothing was changed."
}

/**
 * The name to suggest when saving a result: the source's own name with what
 * was done to it, so "lease.pdf" rotated saves as "lease-rotated.pdf" rather
 * than "document.pdf" beside a dozen others.
 */
fun derivedFileName(sourceName: String, suffix: String, extension: String = "pdf"): String {
    val stem = sourceName.substringBeforeLast('.', missingDelimiterValue = sourceName)
        .trim()
        .ifEmpty { "document" }
    return "$stem-$suffix.$extension"
}

/**
 * A size a person reads, not a byte count. 0 means unknown and says so.
 *
 * Decimal units, deliberately. Android's own file manager shows sizes in
 * thousands — the system picker lists a file as "549 kB" — and this used to
 * divide by 1024, so the very same file read "536 KB" one tap later inside the
 * app. Two different numbers for one file, seconds apart, reads as a mistake.
 */
fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "size unknown"
    bytes < KB -> "$bytes B"
    bytes < MB -> "${(bytes + KB / 2) / KB} kB"
    else -> "%.1f MB".format(bytes.toDouble() / MB)
}

private fun pageWord(count: Int) = if (count == 1) "1 page" else "$count pages"

private const val KB = 1000L
private const val MB = 1000L * 1000L

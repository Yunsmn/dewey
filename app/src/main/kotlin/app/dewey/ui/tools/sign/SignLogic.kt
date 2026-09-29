package app.dewey.ui.tools.sign

import android.net.Uri
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.toolFailureMessage

/**
 * Whether a sign run has everything it needs: a document, a signature drawn
 * or reused, and a page number that actually exists in the document.
 */
fun canRunSign(hasSource: Boolean, hasSignature: Boolean, pageNumber: Int?, pageCount: Int?): Boolean {
    if (!hasSource || !hasSignature || pageCount == null || pageNumber == null) return false
    return pageNumber in 1..pageCount
}

/** "Added your signature to page 3." */
fun signSummary(pageNumber: Int): String = "Added your signature to page $pageNumber."

/** Where a sign run lands: nothing here is a secret, so nothing is cleared on success. */
fun SignUiState.afterRun(result: Result<Unit>, target: Uri): SignUiState = result.fold(
    onSuccess = { copy(run = ToolRunState.Done(signSummary(pageNumber ?: 1), target)) },
    onFailure = { copy(run = ToolRunState.Failed(toolFailureMessage(it))) },
)

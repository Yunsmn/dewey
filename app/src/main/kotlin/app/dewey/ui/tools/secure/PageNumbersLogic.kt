package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.toolFailureMessage

/** Whether a page-numbers run has a document and a starting number that makes sense. */
fun canRunPageNumbers(hasSource: Boolean, startingNumber: Int?): Boolean =
    hasSource && startingNumber != null && startingNumber >= 1

/**
 * What to tell the user after numbering every page.
 *
 * [pageCount] is null while it is still being read; the sentence still says
 * something true in that case rather than waiting on it or lying with a
 * placeholder count.
 */
fun pageNumbersSummary(pageCount: Int?, startingNumber: Int, showTotal: Boolean): String {
    if (pageCount == null) return "Numbered every page."

    val pages = if (pageCount == 1) "1 page" else "$pageCount pages"
    val startNote = if (startingNumber == 1) "" else ", starting at $startingNumber"
    val totalNote = if (showTotal) ", shown as \"N / $pageCount\"" else ""
    return "Numbered $pages$startNote$totalNote."
}

/** Where a page-numbers run lands: nothing here is a secret, so nothing is cleared on success. */
fun PageNumbersUiState.afterRun(result: Result<Unit>, target: Uri): PageNumbersUiState = result.fold(
    onSuccess = {
        copy(run = ToolRunState.Done(pageNumbersSummary(pageCount, startingNumber ?: 1, showTotal), target))
    },
    onFailure = { copy(run = ToolRunState.Failed(toolFailureMessage(it))) },
)

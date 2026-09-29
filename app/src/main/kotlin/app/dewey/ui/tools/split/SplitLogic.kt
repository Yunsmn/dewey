package app.dewey.ui.tools.split

import app.dewey.pdf.SplitOperationException
import app.dewey.pdf.SplitOperations
import app.dewey.pdf.SplitOutcome
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.toolFailureMessage

/**
 * Whether a split run has everything it needs: a document, its page count —
 * split can't validate either mode's input without knowing how many pages
 * there are — somewhere to save the parts, and a mode-appropriate value
 * already typed in.
 */
fun canRunSplit(
    hasSource: Boolean,
    pageCount: Int?,
    hasDestination: Boolean,
    mode: SplitMode,
    everyN: Int?,
    rangesText: String,
): Boolean {
    if (!hasSource || pageCount == null || !hasDestination) return false
    return when (mode) {
        SplitMode.EVERY_N_PAGES -> everyN != null && everyN >= 1
        SplitMode.BY_RANGES -> rangesText.isNotBlank()
    }
}

/** "Split into 4 PDFs.", with honest wording for a run that lost a part. */
fun splitSummary(outcome: SplitOutcome): String {
    if (outcome.isComplete) {
        return "Split into ${fileWord(outcome.written)}."
    }
    val failedSentence = partListSentence(outcome.failedParts)
    return "Split into ${outcome.written} of ${fileWord(outcome.parts)}. $failedSentence couldn't be saved."
}

/**
 * The sentence a split run shows when it fails outright — a bad range or page
 * size typed in, or the source itself unreadable.
 *
 * [SplitOperationException] is named here rather than added to
 * [app.dewey.ui.tools.toolFailureMessage]: that function lives outside this
 * tool's own files, so a new engine failure gets its wording here instead,
 * falling back to the shared one for everything this tool didn't introduce.
 */
fun splitFailureMessage(error: Throwable): String = when (error) {
    is SplitOperationException -> when (val issue = error.issue) {
        is SplitOperations.Issue.EmptyInput -> "Enter how to split the pages, like 1-3, 4-7."
        is SplitOperations.Issue.InvalidPageSize -> "Enter how many pages per file — at least 1."
        is SplitOperations.Issue.MalformedToken -> "\"${issue.token}\" isn't a page number or a range."
        is SplitOperations.Issue.ReversedRange -> "\"${issue.token}\" runs backwards — try it the other way round."
        is SplitOperations.Issue.PageOutOfRange ->
            "There is no page ${issue.page} — this document has ${pageWord(issue.pageCount)}."
        is SplitOperations.Issue.OverlappingPage -> "Page ${issue.page} is named in more than one group."
    }
    else -> toolFailureMessage(error)
}

/**
 * Where a split run lands: several files come out of one run rather than
 * one, so [ToolRunState.Done]'s own output stays null — there is no single
 * result to hand back for reopening.
 */
fun SplitUiState.afterRun(result: Result<SplitOutcome>): SplitUiState = result.fold(
    onSuccess = { outcome -> copy(run = ToolRunState.Done(splitSummary(outcome))) },
    onFailure = { copy(run = ToolRunState.Failed(splitFailureMessage(it))) },
)

private fun fileWord(count: Int): String = if (count == 1) "1 PDF" else "$count PDFs"
private fun pageWord(count: Int): String = if (count == 1) "1 page" else "$count pages"

/** "Part 2", "Parts 1 and 3", or "Parts 1, 3 and 5" — never a bare list of numbers. */
private fun partListSentence(parts: List<Int>): String {
    val label = if (parts.size == 1) "Part" else "Parts"
    val list = when (parts.size) {
        1 -> "${parts[0]}"
        else -> "${parts.dropLast(1).joinToString(", ")} and ${parts.last()}"
    }
    return "$label $list"
}

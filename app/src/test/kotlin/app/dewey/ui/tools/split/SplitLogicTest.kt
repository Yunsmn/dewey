package app.dewey.ui.tools.split

import app.dewey.pdf.SplitOperationException
import app.dewey.pdf.SplitOperations
import app.dewey.pdf.SplitOutcome
import app.dewey.ui.tools.ToolRunState
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SplitLogicTest {

    // -- canRunSplit ----------------------------------------------------

    @Test
    fun `every-N-pages mode needs a source, a page count, a destination and a size of at least one`() {
        assertThat(
            canRunSplit(hasSource = false, pageCount = 10, hasDestination = true, mode = SplitMode.EVERY_N_PAGES, everyN = 2, rangesText = ""),
        ).isFalse()
        assertThat(
            canRunSplit(hasSource = true, pageCount = null, hasDestination = true, mode = SplitMode.EVERY_N_PAGES, everyN = 2, rangesText = ""),
        ).isFalse()
        assertThat(
            canRunSplit(hasSource = true, pageCount = 10, hasDestination = false, mode = SplitMode.EVERY_N_PAGES, everyN = 2, rangesText = ""),
        ).isFalse()
        assertThat(
            canRunSplit(hasSource = true, pageCount = 10, hasDestination = true, mode = SplitMode.EVERY_N_PAGES, everyN = 0, rangesText = ""),
        ).isFalse()
        assertThat(
            canRunSplit(hasSource = true, pageCount = 10, hasDestination = true, mode = SplitMode.EVERY_N_PAGES, everyN = null, rangesText = ""),
        ).isFalse()
        assertThat(
            canRunSplit(hasSource = true, pageCount = 10, hasDestination = true, mode = SplitMode.EVERY_N_PAGES, everyN = 3, rangesText = ""),
        ).isTrue()
    }

    @Test
    fun `by-ranges mode needs non-blank ranges text rather than a valid every-N value`() {
        assertThat(
            canRunSplit(hasSource = true, pageCount = 10, hasDestination = true, mode = SplitMode.BY_RANGES, everyN = null, rangesText = ""),
        ).isFalse()
        assertThat(
            canRunSplit(hasSource = true, pageCount = 10, hasDestination = true, mode = SplitMode.BY_RANGES, everyN = null, rangesText = "   "),
        ).isFalse()
        assertThat(
            canRunSplit(hasSource = true, pageCount = 10, hasDestination = true, mode = SplitMode.BY_RANGES, everyN = null, rangesText = "1-3"),
        ).isTrue()
    }

    // -- splitSummary -----------------------------------------------------

    @Test
    fun `a complete split reports a plain file count`() {
        val outcome = SplitOutcome(parts = 4, written = 4, failedParts = emptyList())
        assertThat(splitSummary(outcome)).isEqualTo("Split into 4 PDFs.")
    }

    @Test
    fun `a single-part split is not described as 1 PDFs`() {
        val outcome = SplitOutcome(parts = 1, written = 1, failedParts = emptyList())
        assertThat(splitSummary(outcome)).isEqualTo("Split into 1 PDF.")
    }

    @Test
    fun `one failed part is named, not just counted`() {
        val outcome = SplitOutcome(parts = 4, written = 3, failedParts = listOf(2))
        assertThat(splitSummary(outcome)).isEqualTo("Split into 3 of 4 PDFs. Part 2 couldn't be saved.")
    }

    @Test
    fun `several failed parts read as a sentence, not a raw list`() {
        val outcome = SplitOutcome(parts = 5, written = 2, failedParts = listOf(1, 3, 5))
        assertThat(splitSummary(outcome)).isEqualTo("Split into 2 of 5 PDFs. Parts 1, 3 and 5 couldn't be saved.")
    }

    // -- splitFailureMessage ------------------------------------------------

    @Test
    fun `each split issue reads as a specific, actionable sentence`() {
        assertThat(splitFailureMessage(SplitOperationException(SplitOperations.Issue.EmptyInput(""))))
            .isEqualTo("Enter how to split the pages, like 1-3, 4-7.")
        assertThat(splitFailureMessage(SplitOperationException(SplitOperations.Issue.InvalidPageSize(0))))
            .isEqualTo("Enter how many pages per file — at least 1.")
        assertThat(splitFailureMessage(SplitOperationException(SplitOperations.Issue.MalformedToken("abc"))))
            .isEqualTo("\"abc\" isn't a page number or a range.")
        assertThat(splitFailureMessage(SplitOperationException(SplitOperations.Issue.ReversedRange("5-2"))))
            .isEqualTo("\"5-2\" runs backwards — try it the other way round.")
        assertThat(splitFailureMessage(SplitOperationException(SplitOperations.Issue.PageOutOfRange(9, 5))))
            .isEqualTo("There is no page 9 — this document has 5 pages.")
        assertThat(splitFailureMessage(SplitOperationException(SplitOperations.Issue.OverlappingPage(2))))
            .isEqualTo("Page 2 is named in more than one group.")
    }

    @Test
    fun `an unrecognized failure falls back to the shared message`() {
        assertThat(splitFailureMessage(RuntimeException("boom"))).isEqualTo("Something went wrong. Nothing was changed.")
    }

    // -- afterRun -----------------------------------------------------------

    @Test
    fun `a successful run reports the summary with no single file to reopen`() {
        val state = SplitUiState(rangesText = "1-3, 4-7")

        val after = state.afterRun(Result.success(SplitOutcome(parts = 2, written = 2, failedParts = emptyList())))

        assertThat(after.run).isEqualTo(ToolRunState.Done("Split into 2 PDFs."))
    }

    @Test
    fun `a failed run is reported without losing the chosen options`() {
        val state = SplitUiState(mode = SplitMode.BY_RANGES, rangesText = "1-3")

        val after = state.afterRun(Result.failure(RuntimeException("boom")))

        assertThat(after.run).isEqualTo(ToolRunState.Failed("Something went wrong. Nothing was changed."))
        assertThat(after.mode).isEqualTo(SplitMode.BY_RANGES)
        assertThat(after.rangesText).isEqualTo("1-3")
    }
}

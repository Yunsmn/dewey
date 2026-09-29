package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The grouping arithmetic behind Split: turning "every N pages" or a typed
 * range spec into the page groups [split] later builds and saves one at a
 * time. All of it 1-based in (what a user types), 0-based out (what a
 * [com.tom_roush.pdfbox.pdmodel.PDPageTree] wants) — the same convention
 * [PageOperations] uses, and the same reason to check it carefully: a typo in
 * the boundary arithmetic here silently drops or duplicates a page across
 * files rather than crashing.
 */
class SplitOperationsTest {

    private fun issueOf(result: Result<*>): SplitOperations.Issue =
        (result.exceptionOrNull() as SplitOperationException).issue

    // -- everyNPages ------------------------------------------------------

    @Test
    fun `a page count that divides evenly makes equal parts`() {
        val groups = SplitOperations.everyNPages(pageCount = 9, size = 3).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
        ).inOrder()
    }

    @Test
    fun `a remainder becomes one shorter final part`() {
        val groups = SplitOperations.everyNPages(pageCount = 11, size = 3).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(
            listOf(0, 1, 2),
            listOf(3, 4, 5),
            listOf(6, 7, 8),
            listOf(9, 10),
        ).inOrder()
    }

    @Test
    fun `one page per file names every page its own part`() {
        val groups = SplitOperations.everyNPages(pageCount = 4, size = 1).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(listOf(0), listOf(1), listOf(2), listOf(3)).inOrder()
    }

    @Test
    fun `a size at least the whole document makes a single part`() {
        val groups = SplitOperations.everyNPages(pageCount = 5, size = 50).getOrThrow()

        assertThat(groups).hasSize(1)
        assertThat(groups[0].pages).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `zero pages per file is rejected`() {
        val result = SplitOperations.everyNPages(pageCount = 10, size = 0)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.InvalidPageSize(0))
    }

    @Test
    fun `a negative pages-per-file is rejected the same as zero`() {
        val result = SplitOperations.everyNPages(pageCount = 10, size = -3)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.InvalidPageSize(-3))
    }

    // -- byRanges -----------------------------------------------------------

    @Test
    fun `three ranges become three groups in order`() {
        val groups = SplitOperations.byRanges("1-3, 4-7, 8-11", pageCount = 11).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(
            listOf(0, 1, 2),
            listOf(3, 4, 5, 6),
            listOf(7, 8, 9, 10),
        ).inOrder()
    }

    @Test
    fun `single page tokens each become their own one-page group`() {
        val groups = SplitOperations.byRanges("1,2,3", pageCount = 3).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(listOf(0), listOf(1), listOf(2)).inOrder()
    }

    @Test
    fun `surrounding and internal whitespace is ignored`() {
        val groups = SplitOperations.byRanges(" 1 - 2 , 3 - 4 ", pageCount = 4).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(listOf(0, 1), listOf(2, 3)).inOrder()
    }

    @Test
    fun `stray commas are tolerated rather than rejected`() {
        val groups = SplitOperations.byRanges("1-2,,3-4,", pageCount = 4).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(listOf(0, 1), listOf(2, 3)).inOrder()
    }

    @Test
    fun `a document need not be split all the way through`() {
        // Naming only "1-2" of a 5-page document is a valid ask: one part
        // holding the first two pages, the rest simply not exported.
        val groups = SplitOperations.byRanges("1-2", pageCount = 5).getOrThrow()

        assertThat(groups).hasSize(1)
        assertThat(groups[0].pages).containsExactly(0, 1)
    }

    @Test
    fun `an empty spec is rejected`() {
        val result = SplitOperations.byRanges("", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.EmptyInput(""))
    }

    @Test
    fun `whitespace only is rejected the same as empty`() {
        val result = SplitOperations.byRanges("   ", pageCount = 5)
        assertThat(issueOf(result)).isInstanceOf(SplitOperations.Issue.EmptyInput::class.java)
    }

    @Test
    fun `commas with nothing usable between them are rejected the same as empty`() {
        val result = SplitOperations.byRanges(",,,", pageCount = 5)
        assertThat(issueOf(result)).isInstanceOf(SplitOperations.Issue.EmptyInput::class.java)
    }

    @Test
    fun `text that is not a number is malformed`() {
        val result = SplitOperations.byRanges("abc", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.MalformedToken("abc"))
    }

    @Test
    fun `a dash with nothing on one side is malformed`() {
        val result = SplitOperations.byRanges("3-", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.MalformedToken("3-"))
    }

    @Test
    fun `a page zero is out of range, not a valid page before the first`() {
        val result = SplitOperations.byRanges("0-2", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.PageOutOfRange(0, 5))
    }

    @Test
    fun `a page past the end is out of range`() {
        val result = SplitOperations.byRanges("1-6", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.PageOutOfRange(6, 5))
    }

    @Test
    fun `a reversed range is rejected rather than walked backwards`() {
        // Unlike PageOperations.parsePageRange, which reads "5-3" as 5,4,3 —
        // reversing the page order within one split-off file has no sensible
        // reading, so this is refused instead of silently reversing it.
        val result = SplitOperations.byRanges("5-3", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.ReversedRange("5-3"))
    }

    @Test
    fun `a page named in two groups is rejected`() {
        val result = SplitOperations.byRanges("1-3,2-4", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.OverlappingPage(2))
    }

    @Test
    fun `a page repeated as two single-page tokens is rejected the same way`() {
        val result = SplitOperations.byRanges("2,2", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(SplitOperations.Issue.OverlappingPage(2))
    }

    @Test
    fun `adjacent ranges that touch but do not overlap are both accepted`() {
        val groups = SplitOperations.byRanges("1-2,3-4", pageCount = 4).getOrThrow()
        assertThat(groups.map { it.pages }).containsExactly(listOf(0, 1), listOf(2, 3)).inOrder()
    }

    @Test
    fun `an en dash from a phone keyboard reads as a range`() {
        val groups = SplitOperations.byRanges("1\u20133, 4\u20145", pageCount = 5).getOrThrow()

        assertThat(groups.map { it.pages }).containsExactly(listOf(0, 1, 2), listOf(3, 4)).inOrder()
    }
}

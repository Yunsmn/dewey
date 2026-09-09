package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The page-index arithmetic behind merge/extract/rotate/reorder/delete.
 *
 * All of it is 1-based in, 0-based out — what a user types into a text
 * field versus what PDFBox's page tree wants — and page ranges are user
 * input from that same text field, so the interesting cases are the ones a
 * real person's typing produces: reversed ranges, repeated pages, stray
 * commas, and getting page 1 and the last page exactly right.
 */
class PageOperationsTest {

    private fun issueOf(result: Result<*>): PageOperations.Issue =
        (result.exceptionOrNull() as PageOperationException).issue

    // -- parsePageRange -------------------------------------------------

    @Test
    fun `a mix of runs and single pages is read in order`() {
        val result = PageOperations.parsePageRange("1-3,7,9-11", pageCount = 12)
        assertThat(result.getOrNull()).containsExactly(0, 1, 2, 6, 8, 9, 10).inOrder()
    }

    @Test
    fun `a reversed range is read in the order the user wrote it`() {
        val result = PageOperations.parsePageRange("5-3", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(4, 3, 2).inOrder()
    }

    @Test
    fun `a repeated page appears once per mention`() {
        val result = PageOperations.parsePageRange("2,2,2", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(1, 1, 1).inOrder()
    }

    @Test
    fun `surrounding and internal whitespace is ignored`() {
        val result = PageOperations.parsePageRange(" 1 - 3 , 5 ", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(0, 1, 2, 4).inOrder()
    }

    @Test
    fun `stray commas are tolerated rather than rejected`() {
        val result = PageOperations.parsePageRange("1,,3,", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(0, 2).inOrder()
    }

    @Test
    fun `an empty string is rejected`() {
        val result = PageOperations.parsePageRange("", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.EmptyRange(""))
    }

    @Test
    fun `whitespace only is rejected the same as empty`() {
        val result = PageOperations.parsePageRange("   ", pageCount = 5)
        assertThat(issueOf(result)).isInstanceOf(PageOperations.Issue.EmptyRange::class.java)
    }

    @Test
    fun `commas with nothing usable between them are rejected the same as empty`() {
        val result = PageOperations.parsePageRange(",,,", pageCount = 5)
        assertThat(issueOf(result)).isInstanceOf(PageOperations.Issue.EmptyRange::class.java)
    }

    @Test
    fun `text that is not a number is malformed`() {
        val result = PageOperations.parsePageRange("abc", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.MalformedToken("abc"))
    }

    @Test
    fun `a dash with nothing on one side is malformed`() {
        val result = PageOperations.parsePageRange("3-", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.MalformedToken("3-"))
    }

    @Test
    fun `page zero is out of range, not a valid page before the first`() {
        val result = PageOperations.parsePageRange("0", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.PageOutOfRange(0, 5))
    }

    @Test
    fun `one page past the end is out of range`() {
        val result = PageOperations.parsePageRange("6", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.PageOutOfRange(6, 5))
    }

    @Test
    fun `the first page is index zero`() {
        val result = PageOperations.parsePageRange("1", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(0)
    }

    @Test
    fun `the last page is index pageCount minus one`() {
        val result = PageOperations.parsePageRange("5", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(4)
    }

    @Test
    fun `a single page document accepts only page one`() {
        assertThat(PageOperations.parsePageRange("1", pageCount = 1).getOrNull()).containsExactly(0)
        assertThat(
            issueOf(PageOperations.parsePageRange("2", pageCount = 1))
        ).isEqualTo(PageOperations.Issue.PageOutOfRange(2, 1))
    }

    @Test
    fun `the whole document is every index from zero to pageCount minus one`() {
        val result = PageOperations.parsePageRange("1-12", pageCount = 12)
        assertThat(result.getOrNull()).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11).inOrder()
    }

    @Test
    fun `an out-of-range end of a run is rejected even when the start is valid`() {
        val result = PageOperations.parsePageRange("1-99", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.PageOutOfRange(99, 5))
    }

    // -- parsePageDeletion ------------------------------------------------

    @Test
    fun `deletion drops duplicates and sorts ascending regardless of input order`() {
        val result = PageOperations.parsePageDeletion("5-3,1,1", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(0, 2, 3, 4).inOrder()
    }

    @Test
    fun `deleting every page is refused rather than producing an empty document`() {
        val result = PageOperations.parsePageDeletion("1-5", pageCount = 5)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.WouldEmptyDocument(5))
    }

    @Test
    fun `deleting every page via duplicated mentions is still refused`() {
        val result = PageOperations.parsePageDeletion("1,1,1", pageCount = 1)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.WouldEmptyDocument(1))
    }

    @Test
    fun `deleting all but one page is allowed`() {
        val result = PageOperations.parsePageDeletion("1-4", pageCount = 5)
        assertThat(result.getOrNull()).containsExactly(0, 1, 2, 3)
    }

    @Test
    fun `an invalid range still fails deletion the same way it fails parsing`() {
        val result = PageOperations.parsePageDeletion("", pageCount = 5)
        assertThat(issueOf(result)).isInstanceOf(PageOperations.Issue.EmptyRange::class.java)
    }

    // -- parsePageMove ----------------------------------------------------

    @Test
    fun `moving the first page to the end shifts everything else up`() {
        val result = PageOperations.parsePageMove(pageCount = 5, from = 1, to = 5)
        assertThat(result.getOrNull()).containsExactly(1, 2, 3, 4, 0).inOrder()
    }

    @Test
    fun `moving the last page to the front shifts everything else back`() {
        val result = PageOperations.parsePageMove(pageCount = 5, from = 5, to = 1)
        assertThat(result.getOrNull()).containsExactly(4, 0, 1, 2, 3).inOrder()
    }

    @Test
    fun `moving a page to its own position changes nothing`() {
        val result = PageOperations.parsePageMove(pageCount = 5, from = 3, to = 3)
        assertThat(result.getOrNull()).containsExactly(0, 1, 2, 3, 4).inOrder()
    }

    @Test
    fun `moving a page to the position just after itself is a single swap`() {
        val result = PageOperations.parsePageMove(pageCount = 5, from = 2, to = 3)
        assertThat(result.getOrNull()).containsExactly(0, 2, 1, 3, 4).inOrder()
    }

    @Test
    fun `a source page number out of range is rejected`() {
        val result = PageOperations.parsePageMove(pageCount = 5, from = 6, to = 1)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.PageOutOfRange(6, 5))
    }

    @Test
    fun `a target page number out of range is rejected`() {
        val result = PageOperations.parsePageMove(pageCount = 5, from = 1, to = 0)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.PageOutOfRange(0, 5))
    }

    @Test
    fun `the only page in a single page document can only move to itself`() {
        val result = PageOperations.parsePageMove(pageCount = 1, from = 1, to = 1)
        assertThat(result.getOrNull()).containsExactly(0)
    }

    // -- normalizedRotationDegrees -----------------------------------------

    @Test
    fun `a quarter turn clockwise from zero is ninety`() {
        assertThat(PageOperations.normalizedRotationDegrees(current = 0, delta = 90)).isEqualTo(90)
    }

    @Test
    fun `rotating past three hundred sixty wraps back to zero`() {
        assertThat(PageOperations.normalizedRotationDegrees(current = 270, delta = 90)).isEqualTo(0)
    }

    @Test
    fun `a negative delta rotates counter-clockwise without going negative`() {
        // Kotlin's % keeps the sign of the dividend: -90 % 360 is -90, not
        // 270. This is exactly the case that would leak that footgun.
        assertThat(PageOperations.normalizedRotationDegrees(current = 0, delta = -90)).isEqualTo(270)
    }

    @Test
    fun `a full turn returns to the same orientation`() {
        assertThat(PageOperations.normalizedRotationDegrees(current = 45, delta = 360)).isEqualTo(45)
    }

    // -- validateRotationDelta ---------------------------------------------

    @Test
    fun `ninety, one-eighty and two-seventy are all valid deltas`() {
        assertThat(PageOperations.validateRotationDelta(90).isSuccess).isTrue()
        assertThat(PageOperations.validateRotationDelta(180).isSuccess).isTrue()
        assertThat(PageOperations.validateRotationDelta(270).isSuccess).isTrue()
        assertThat(PageOperations.validateRotationDelta(-90).isSuccess).isTrue()
    }

    @Test
    fun `a zero-degree rotation is rejected as meaningless`() {
        val result = PageOperations.validateRotationDelta(0)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.InvalidRotation(0))
    }

    @Test
    fun `a rotation that is not a multiple of ninety is rejected`() {
        val result = PageOperations.validateRotationDelta(45)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.InvalidRotation(45))
    }

    // -- validateMergeSourceCount --------------------------------------------

    @Test
    fun `two documents is the minimum a merge accepts`() {
        assertThat(PageOperations.validateMergeSourceCount(2).isSuccess).isTrue()
    }

    @Test
    fun `a single document is not a merge`() {
        val result = PageOperations.validateMergeSourceCount(1)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.TooFewDocuments(1))
    }

    @Test
    fun `zero documents is not a merge`() {
        val result = PageOperations.validateMergeSourceCount(0)
        assertThat(issueOf(result)).isEqualTo(PageOperations.Issue.TooFewDocuments(0))
    }
}

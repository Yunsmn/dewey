package app.dewey.ui.tools.secure

import android.net.Uri
import app.dewey.pdf.Corner
import app.dewey.ui.tools.ToolRunState
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class PageNumbersLogicTest {

    private val target = mockk<Uri>(relaxed = true)

    // -- canRunPageNumbers --------------------------------------------------

    @Test
    fun `needs a source and a starting number of at least 1`() {
        assertThat(canRunPageNumbers(hasSource = false, startingNumber = 1)).isFalse()
        assertThat(canRunPageNumbers(hasSource = true, startingNumber = null)).isFalse()
        assertThat(canRunPageNumbers(hasSource = true, startingNumber = 0)).isFalse()
        assertThat(canRunPageNumbers(hasSource = true, startingNumber = -3)).isFalse()
        assertThat(canRunPageNumbers(hasSource = true, startingNumber = 1)).isTrue()
    }

    @Test
    fun `a blank or non-numeric starting number parses to null, not zero`() {
        assertThat(PageNumbersUiState(startingNumberText = "").startingNumber).isNull()
        assertThat(PageNumbersUiState(startingNumberText = "abc").startingNumber).isNull()
        assertThat(PageNumbersUiState(startingNumberText = "3").startingNumber).isEqualTo(3)
    }

    // -- pageNumbersSummary ------------------------------------------------------

    @Test
    fun `a plain run just names the page count`() {
        assertThat(pageNumbersSummary(pageCount = 12, startingNumber = 1, showTotal = false))
            .isEqualTo("Numbered 12 pages.")
    }

    @Test
    fun `a single page is not described as 1 pages`() {
        assertThat(pageNumbersSummary(pageCount = 1, startingNumber = 1, showTotal = false))
            .isEqualTo("Numbered 1 page.")
    }

    @Test
    fun `a non-default starting number is named`() {
        assertThat(pageNumbersSummary(pageCount = 12, startingNumber = 5, showTotal = false))
            .isEqualTo("Numbered 12 pages, starting at 5.")
    }

    @Test
    fun `showing the total is said, not just done silently`() {
        assertThat(pageNumbersSummary(pageCount = 12, startingNumber = 1, showTotal = true))
            .isEqualTo("Numbered 12 pages, shown as \"N / 12\".")
    }

    @Test
    fun `an unknown page count still reports plainly instead of waiting on it`() {
        assertThat(pageNumbersSummary(pageCount = null, startingNumber = 1, showTotal = false))
            .isEqualTo("Numbered every page.")
    }

    // -- afterRun ---------------------------------------------------------------

    @Test
    fun `a successful run reports the summary using the loaded page count`() {
        val state = PageNumbersUiState(pageCount = 12, corner = Corner.TOP_RIGHT, startingNumberText = "1")

        val after = state.afterRun(Result.success(Unit), target)

        assertThat(after.run).isEqualTo(ToolRunState.Done(pageNumbersSummary(12, 1, false), target))
    }

    @Test
    fun `a failed run is reported without losing the chosen corner`() {
        val state = PageNumbersUiState(corner = Corner.BOTTOM_LEFT, startingNumberText = "1")

        val after = state.afterRun(Result.failure(RuntimeException("boom")), target)

        assertThat(after.run).isEqualTo(ToolRunState.Failed("Something went wrong. Nothing was changed."))
        assertThat(after.corner).isEqualTo(Corner.BOTTOM_LEFT)
    }
}

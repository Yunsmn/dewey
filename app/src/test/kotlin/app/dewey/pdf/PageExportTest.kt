package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * An export to images says whether it is complete, rather than leaving the
 * caller to compare a count against a number it may never have known.
 */
class PageExportTest {

    @Test
    fun `an export that skipped nothing is complete`() {
        assertThat(PageExport(rendered = 20, skipped = emptyList()).isComplete).isTrue()
    }

    @Test
    fun `an export that skipped a page is not complete however many it rendered`() {
        // The case the old Int return hid: nineteen pages back from a
        // twenty-page document, reported as a plain success.
        val export = PageExport(rendered = 19, skipped = listOf(13))

        assertThat(export.isComplete).isFalse()
        assertThat(export.skipped).containsExactly(13)
    }

    @Test
    fun `an export that rendered nothing and skipped nothing is still complete`() {
        // An empty page list asked for nothing, and got it.
        assertThat(PageExport(rendered = 0, skipped = emptyList()).isComplete).isTrue()
    }
}

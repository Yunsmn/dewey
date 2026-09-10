package app.dewey.ui.tools.pages

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageWordingTest {

    @Test
    fun `one page is singular`() {
        assertThat(pageWord(1)).isEqualTo("1 page")
    }

    @Test
    fun `zero and above are plural`() {
        assertThat(pageWord(0)).isEqualTo("0 pages")
        assertThat(pageWord(4)).isEqualTo("4 pages")
    }
}

package app.dewey.ui.tools.thumbnails

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageSelectionTest {

    // -- toggleSelection ----------------------------------------------------

    @Test
    fun `toggling an unchecked page checks it`() {
        assertThat(toggleSelection(emptySet(), 2)).containsExactly(2)
    }

    @Test
    fun `toggling a checked page unchecks it`() {
        assertThat(toggleSelection(setOf(1, 2, 3), 2)).containsExactly(1, 3)
    }

    @Test
    fun `toggling leaves the rest of the selection untouched`() {
        assertThat(toggleSelection(setOf(0, 4), 4)).containsExactly(0)
    }

    // -- selectAll ------------------------------------------------------

    @Test
    fun `selectAll is every page, 0-based`() {
        assertThat(selectAll(4)).containsExactly(0, 1, 2, 3)
    }

    @Test
    fun `selectAll of zero pages is empty`() {
        assertThat(selectAll(0)).isEmpty()
    }

    // -- specFromSelection ------------------------------------------------

    @Test
    fun `builds a 1-based comma spec from a 0-based selection`() {
        assertThat(specFromSelection(setOf(0, 2, 6))).isEqualTo("1,3,7")
    }

    @Test
    fun `sorts ascending regardless of the set's own iteration order`() {
        assertThat(specFromSelection(setOf(6, 0, 2))).isEqualTo("1,3,7")
    }

    @Test
    fun `an empty selection is an empty spec`() {
        assertThat(specFromSelection(emptySet())).isEqualTo("")
    }

    @Test
    fun `a single page selection has no comma`() {
        assertThat(specFromSelection(setOf(3))).isEqualTo("4")
    }
}

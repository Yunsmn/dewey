package app.dewey.ui.tools.thumbnails

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageOrderLogicTest {

    @Test
    fun `moving an item earlier shifts everything between it and its new spot`() {
        val items = listOf("a", "b", "c", "d")
        assertThat(moveItem(items, from = 3, to = 1)).containsExactly("a", "d", "b", "c").inOrder()
    }

    @Test
    fun `moving an item later shifts everything between it and its new spot`() {
        val items = listOf("a", "b", "c", "d")
        assertThat(moveItem(items, from = 0, to = 2)).containsExactly("b", "c", "a", "d").inOrder()
    }

    @Test
    fun `moving an item to its own position changes nothing`() {
        val items = listOf("a", "b", "c")
        assertThat(moveItem(items, from = 1, to = 1)).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `an out-of-bounds source index is a no-op`() {
        val items = listOf("a", "b")
        assertThat(moveItem(items, from = 5, to = 0)).isEqualTo(items)
    }

    @Test
    fun `an out-of-bounds target index is a no-op`() {
        val items = listOf("a", "b")
        assertThat(moveItem(items, from = 0, to = -1)).isEqualTo(items)
    }

    @Test
    fun `the original list is not mutated`() {
        val items = listOf("a", "b", "c")
        moveItem(items, from = 0, to = 2)
        assertThat(items).containsExactly("a", "b", "c").inOrder()
    }
}

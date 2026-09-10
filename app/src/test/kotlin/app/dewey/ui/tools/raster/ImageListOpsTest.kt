package app.dewey.ui.tools.raster

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageListOpsTest {

    @Test
    fun `moving an item forward shifts everything between it and the target`() {
        assertThat(reorderImage(listOf("a", "b", "c", "d"), from = 0, to = 2))
            .containsExactly("b", "c", "a", "d").inOrder()
    }

    @Test
    fun `moving an item backward shifts everything between the target and it`() {
        assertThat(reorderImage(listOf("a", "b", "c", "d"), from = 3, to = 1))
            .containsExactly("a", "d", "b", "c").inOrder()
    }

    @Test
    fun `moving an item to its own position changes nothing`() {
        val images = listOf("a", "b", "c")

        assertThat(reorderImage(images, from = 1, to = 1)).isEqualTo(images)
    }

    @Test
    fun `an out-of-range move is a no-op rather than a crash`() {
        val images = listOf("a", "b", "c")

        assertThat(reorderImage(images, from = -1, to = 1)).isEqualTo(images)
        assertThat(reorderImage(images, from = 1, to = 9)).isEqualTo(images)
    }

    @Test
    fun `removing an item drops only that one`() {
        assertThat(removeImage(listOf("a", "b", "c"), index = 1)).containsExactly("a", "c").inOrder()
    }

    @Test
    fun `an out-of-range removal is a no-op rather than a crash`() {
        val images = listOf("a", "b", "c")

        assertThat(removeImage(images, index = 9)).isEqualTo(images)
        assertThat(removeImage(images, index = -1)).isEqualTo(images)
    }

    @Test
    fun `reordering never mutates the list handed in`() {
        val images = listOf("a", "b", "c")

        reorderImage(images, from = 0, to = 2)

        assertThat(images).containsExactly("a", "b", "c").inOrder()
    }
}

package app.dewey.ui.tools.pages

import android.net.Uri
import app.dewey.ui.tools.PickedFile
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class ReorderLogicTest {

    private val file = PickedFile(mockk<Uri>(relaxed = true), "lease.pdf", 1_000)

    @Test
    fun `identityOrder is every page, 0-based, in its original order`() {
        assertThat(identityOrder(4)).containsExactly(0, 1, 2, 3).inOrder()
    }

    @Test
    fun `needs a file, a known order, and a known page count`() {
        assertThat(canRunReorder(null, identityOrder(10), 10)).isFalse()
        assertThat(canRunReorder(file, null, 10)).isFalse()
        assertThat(canRunReorder(file, identityOrder(10), null)).isFalse()
    }

    @Test
    fun `does not run while the order still matches the original`() {
        assertThat(canRunReorder(file, identityOrder(10), 10)).isFalse()
    }

    @Test
    fun `runs once the order differs from the original`() {
        val moved = listOf(4, 0, 1, 2, 3)
        assertThat(canRunReorder(file, moved, 5)).isTrue()
    }

    @Test
    fun `reports the new order was saved`() {
        assertThat(reorderSummary()).isEqualTo("Saved the new page order.")
    }
}

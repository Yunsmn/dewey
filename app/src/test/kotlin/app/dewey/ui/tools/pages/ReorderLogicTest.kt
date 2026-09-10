package app.dewey.ui.tools.pages

import android.net.Uri
import app.dewey.ui.tools.PickedFile
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class ReorderLogicTest {

    private val file = PickedFile(mockk<Uri>(relaxed = true), "lease.pdf", 1_000)

    @Test
    fun `needs a file and a known page count`() {
        assertThat(canRunReorder(null, 10, "1", "2")).isFalse()
        assertThat(canRunReorder(file, null, "1", "2")).isFalse()
    }

    @Test
    fun `both positions must parse as whole numbers`() {
        assertThat(canRunReorder(file, 10, "", "2")).isFalse()
        assertThat(canRunReorder(file, 10, "1", "")).isFalse()
        assertThat(canRunReorder(file, 10, "abc", "2")).isFalse()
        assertThat(canRunReorder(file, 10, "1.5", "2")).isFalse()
    }

    @Test
    fun `both positions must be within the document`() {
        assertThat(canRunReorder(file, 10, "0", "2")).isFalse()
        assertThat(canRunReorder(file, 10, "1", "11")).isFalse()
    }

    @Test
    fun `runs once both positions are valid page numbers`() {
        assertThat(canRunReorder(file, 10, "5", "1")).isTrue()
    }

    @Test
    fun `moving a page to its own position still runs - PageOperations treats it as a no-op`() {
        assertThat(canRunReorder(file, 10, "3", "3")).isTrue()
    }

    @Test
    fun `describes which page moved to which position`() {
        assertThat(reorderSummary(from = 5, to = 1)).isEqualTo("Moved page 5 to position 1")
    }
}

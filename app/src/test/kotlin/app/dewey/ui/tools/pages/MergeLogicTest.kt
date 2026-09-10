package app.dewey.ui.tools.pages

import android.net.Uri
import app.dewey.ui.tools.PickedFile
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

/**
 * The merge list's own arithmetic — how many files it takes to run, and the
 * move-up/move-down/remove edits a user makes while arranging them — kept
 * separate from [MergeToolViewModel] so it runs on the plain JVM with no
 * Android framework involved.
 */
class MergeLogicTest {

    private fun file(name: String) = PickedFile(mockk<Uri>(relaxed = true), name, 1_000)

    // -- canRunMerge -----------------------------------------------------

    @Test
    fun `fewer than two files cannot run`() {
        assertThat(canRunMerge(emptyList())).isFalse()
        assertThat(canRunMerge(listOf(file("a.pdf")))).isFalse()
    }

    @Test
    fun `two or more files can run`() {
        assertThat(canRunMerge(listOf(file("a.pdf"), file("b.pdf")))).isTrue()
    }

    // -- mergeSummary ------------------------------------------------------

    @Test
    fun `describes the file count and the merged document's page count`() {
        assertThat(mergeSummary(fileCount = 3, resultPageCount = 41))
            .isEqualTo("Merged 3 PDFs into one 41-page document")
    }

    // -- moveUp / moveDown -------------------------------------------------

    @Test
    fun `moving the first file up is a no-op`() {
        val files = listOf(file("a.pdf"), file("b.pdf"))
        assertThat(moveUp(files, 0)).isEqualTo(files)
    }

    @Test
    fun `moving a file up swaps it with its predecessor`() {
        val files = listOf(file("a.pdf"), file("b.pdf"), file("c.pdf"))
        val moved = moveUp(files, 1)
        assertThat(moved.map { it.name }).containsExactly("b.pdf", "a.pdf", "c.pdf").inOrder()
    }

    @Test
    fun `moving the last file down is a no-op`() {
        val files = listOf(file("a.pdf"), file("b.pdf"))
        assertThat(moveDown(files, 1)).isEqualTo(files)
    }

    @Test
    fun `moving a file down swaps it with its successor`() {
        val files = listOf(file("a.pdf"), file("b.pdf"), file("c.pdf"))
        val moved = moveDown(files, 0)
        assertThat(moved.map { it.name }).containsExactly("b.pdf", "a.pdf", "c.pdf").inOrder()
    }

    @Test
    fun `an out-of-bounds move leaves the list untouched`() {
        val files = listOf(file("a.pdf"))
        assertThat(moveUp(files, 5)).isEqualTo(files)
        assertThat(moveDown(files, -1)).isEqualTo(files)
    }

    // -- removeAt ------------------------------------------------------

    @Test
    fun `removes only the entry at the given index`() {
        val files = listOf(file("a.pdf"), file("b.pdf"), file("c.pdf"))
        val remaining = removeAt(files, 1)
        assertThat(remaining.map { it.name }).containsExactly("a.pdf", "c.pdf").inOrder()
    }

    @Test
    fun `the original list is not mutated`() {
        val files = listOf(file("a.pdf"), file("b.pdf"))
        removeAt(files, 0)
        assertThat(files).hasSize(2)
    }
}

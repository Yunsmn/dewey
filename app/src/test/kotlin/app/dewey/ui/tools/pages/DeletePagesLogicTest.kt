package app.dewey.ui.tools.pages

import android.net.Uri
import app.dewey.ui.tools.PickedFile
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class DeletePagesLogicTest {

    private val file = PickedFile(mockk<Uri>(relaxed = true), "lease.pdf", 1_000)

    @Test
    fun `needs a file, a known page count, and a non-blank range`() {
        assertThat(canRunDelete(null, 10, "2")).isFalse()
        assertThat(canRunDelete(file, null, "2")).isFalse()
        assertThat(canRunDelete(file, 10, "")).isFalse()
        assertThat(canRunDelete(file, 10, "  ")).isFalse()
    }

    @Test
    fun `runs once a file, a page count, and a range are all present`() {
        assertThat(canRunDelete(file, 10, "2,5-6")).isTrue()
    }

    @Test
    fun `summarises how many pages were removed and how many remain`() {
        assertThat(deleteSummary(deletedCount = 2, remainingCount = 9))
            .isEqualTo("Deleted 2 pages; 9 pages remain")
    }

    @Test
    fun `uses singular wording when exactly one page remains`() {
        assertThat(deleteSummary(deletedCount = 9, remainingCount = 1))
            .isEqualTo("Deleted 9 pages; 1 page remains")
    }

    @Test
    fun `uses singular wording when exactly one page is deleted`() {
        assertThat(deleteSummary(deletedCount = 1, remainingCount = 9))
            .isEqualTo("Deleted 1 page; 9 pages remain")
    }
}

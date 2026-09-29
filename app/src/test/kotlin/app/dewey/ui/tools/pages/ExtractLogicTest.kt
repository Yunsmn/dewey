package app.dewey.ui.tools.pages

import android.net.Uri
import app.dewey.ui.tools.PickedFile
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class ExtractLogicTest {

    private val file = PickedFile(mockk<Uri>(relaxed = true), "lease.pdf", 1_000)

    @Test
    fun `needs a file, a known page count, and at least one checked page`() {
        assertThat(canRunExtract(null, 10, setOf(0))).isFalse()
        assertThat(canRunExtract(file, null, setOf(0))).isFalse()
        assertThat(canRunExtract(file, 10, emptySet())).isFalse()
    }

    @Test
    fun `runs once a file, a page count, and a selection are all present`() {
        assertThat(canRunExtract(file, 10, setOf(0, 2))).isTrue()
    }

    @Test
    fun `summarises how many pages were pulled into the new document`() {
        assertThat(extractSummary(4)).isEqualTo("Extracted 4 pages into a new document")
        assertThat(extractSummary(1)).isEqualTo("Extracted 1 page into a new document")
    }
}

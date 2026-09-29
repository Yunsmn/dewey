package app.dewey.ui.tools.pages

import android.net.Uri
import app.dewey.ui.tools.PickedFile
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class RotateLogicTest {

    private val file = PickedFile(mockk<Uri>(relaxed = true), "lease.pdf", 1_000)

    // -- canRunRotate -------------------------------------------------

    @Test
    fun `needs a file, a known page count, a checked page, and a chosen direction`() {
        assertThat(canRunRotate(null, 10, setOf(0), 90)).isFalse()
        assertThat(canRunRotate(file, null, setOf(0), 90)).isFalse()
        assertThat(canRunRotate(file, 10, emptySet(), 90)).isFalse()
        assertThat(canRunRotate(file, 10, setOf(0), null)).isFalse()
    }

    @Test
    fun `runs once every page is selected and a direction is chosen`() {
        assertThat(canRunRotate(file, 10, (0 until 10).toSet(), 90)).isTrue()
    }

    @Test
    fun `runs with only some pages selected too`() {
        assertThat(canRunRotate(file, 10, setOf(2, 4), 90)).isTrue()
    }

    // -- rotateSummary -------------------------------------------------

    @Test
    fun `summarises how many pages turned and by how much`() {
        assertThat(rotateSummary(rotatedCount = 4, degrees = 90))
            .isEqualTo("Rotated 4 pages 90° clockwise")
    }
}

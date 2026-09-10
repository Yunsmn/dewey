package app.dewey.ui.tools.pages

import android.net.Uri
import app.dewey.ui.tools.PickedFile
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

class RotateLogicTest {

    private val file = PickedFile(mockk<Uri>(relaxed = true), "lease.pdf", 1_000)

    // -- effectiveRotateRange ---------------------------------------------

    @Test
    fun `a blank range means every page`() {
        assertThat(effectiveRotateRange("", pageCount = 6)).isEqualTo("1-6")
        assertThat(effectiveRotateRange("   ", pageCount = 6)).isEqualTo("1-6")
    }

    @Test
    fun `a typed range is left as the user wrote it`() {
        assertThat(effectiveRotateRange("1-3,5", pageCount = 6)).isEqualTo("1-3,5")
    }

    // -- canRunRotate -------------------------------------------------

    @Test
    fun `needs a file, a known page count, and a chosen direction`() {
        assertThat(canRunRotate(null, 10, 90)).isFalse()
        assertThat(canRunRotate(file, null, 90)).isFalse()
        assertThat(canRunRotate(file, 10, null)).isFalse()
    }

    @Test
    fun `blank range text does not block running - it means every page`() {
        assertThat(canRunRotate(file, 10, 90)).isTrue()
    }

    // -- rotateSummary -------------------------------------------------

    @Test
    fun `summarises how many pages turned and by how much`() {
        assertThat(rotateSummary(rotatedCount = 4, degrees = 90))
            .isEqualTo("Rotated 4 pages 90° clockwise")
    }
}

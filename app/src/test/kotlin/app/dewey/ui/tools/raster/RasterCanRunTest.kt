package app.dewey.ui.tools.raster

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RasterCanRunTest {

    @Test
    fun `pdf to images needs both a source and a destination`() {
        assertThat(canRunPdfToImages(hasSource = false, hasDestination = false)).isFalse()
        assertThat(canRunPdfToImages(hasSource = true, hasDestination = false)).isFalse()
        assertThat(canRunPdfToImages(hasSource = false, hasDestination = true)).isFalse()
        assertThat(canRunPdfToImages(hasSource = true, hasDestination = true)).isTrue()
    }

    @Test
    fun `images to pdf needs at least one image`() {
        assertThat(canRunImagesToPdf(imageCount = 0)).isFalse()
        assertThat(canRunImagesToPdf(imageCount = 1)).isTrue()
    }

    @Test
    fun `compress needs a source`() {
        assertThat(canRunCompress(hasSource = false)).isFalse()
        assertThat(canRunCompress(hasSource = true)).isTrue()
    }
}

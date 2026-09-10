package app.dewey.ui.tools.raster

import app.dewey.pdf.CompressionResult
import app.dewey.pdf.PageExport
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class RasterSummariesTest {

    @Test
    fun `a complete export reports a plain page count`() {
        val export = PageExport(rendered = 12, skipped = emptyList())

        assertThat(pdfToImagesSummary(export)).isEqualTo("Saved 12 pages.")
    }

    @Test
    fun `a single-page export is not described as 1 pages`() {
        val export = PageExport(rendered = 1, skipped = emptyList())

        assertThat(pdfToImagesSummary(export)).isEqualTo("Saved 1 page.")
    }

    @Test
    fun `one skipped page is named, not just counted`() {
        val export = PageExport(rendered = 11, skipped = listOf(6))

        assertThat(pdfToImagesSummary(export)).isEqualTo("Saved 11 of 12 pages. Page 7 couldn't be saved.")
    }

    @Test
    fun `several skipped pages read as a sentence, not a raw list`() {
        val export = PageExport(rendered = 10, skipped = listOf(4, 0))

        // Indices are 0-based and out of order coming in; the sentence is
        // 1-based and sorted.
        assertThat(pdfToImagesSummary(export)).isEqualTo("Saved 10 of 12 pages. Pages 1 and 5 couldn't be saved.")
    }

    @Test
    fun `three or more skipped pages use a comma list with a trailing and`() {
        val export = PageExport(rendered = 9, skipped = listOf(0, 2, 4))

        assertThat(pdfToImagesSummary(export))
            .isEqualTo("Saved 9 of 12 pages. Pages 1, 3 and 5 couldn't be saved.")
    }

    @Test
    fun `placing every chosen image reports a plain count`() {
        assertThat(imagesToPdfSummary(placed = 5, chosen = 5)).isEqualTo("Placed 5 images into the PDF.")
    }

    @Test
    fun `placing a single image is not described as 1 images`() {
        assertThat(imagesToPdfSummary(placed = 1, chosen = 1)).isEqualTo("Placed 1 image into the PDF.")
    }

    @Test
    fun `unreadable images are named as a shortfall, not hidden in the count`() {
        assertThat(imagesToPdfSummary(placed = 10, chosen = 12))
            .isEqualTo("Placed 10 of 12 images into the PDF. 2 images couldn't be read.")
    }

    @Test
    fun `a smaller result reports how much was saved and the percentage`() {
        Locale.setDefault(Locale.US)
        val result = CompressionResult(originalBytes = 1_000_000, resultBytes = 750_000)

        assertThat(compressionSummary(result)).isEqualTo("Saved 244 KB (25% smaller).")
    }

    @Test
    fun `a larger result says so plainly instead of claiming success`() {
        val result = CompressionResult(originalBytes = 1_000_000, resultBytes = 1_200_000)

        assertThat(compressionSummary(result))
            .isEqualTo("The result came out larger than the original — this PDF is probably already compact.")
    }

    @Test
    fun `an unknown result size is reported as unknown, not as a success`() {
        val result = CompressionResult(originalBytes = 1_000_000, resultBytes = null)

        assertThat(compressionSummary(result)).isEqualTo("Compressed, but the new size couldn't be checked.")
    }

    @Test
    fun `percentage rounding is half-up and never leaves the 0 to 100 range`() {
        assertThat(percentSmaller(1000, 750)).isEqualTo(25)
        assertThat(percentSmaller(1000, 667)).isEqualTo(33)
        assertThat(percentSmaller(3, 1)).isEqualTo(67)
        assertThat(percentSmaller(1000, 1000)).isEqualTo(0)
        assertThat(percentSmaller(0, 500)).isEqualTo(0)
    }
}

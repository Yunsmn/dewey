package app.dewey.ui.tools

import app.dewey.pdf.PageOperationException
import app.dewey.pdf.PageOperations
import app.dewey.pdf.PasswordCheck
import app.dewey.pdf.PdfToolException
import app.dewey.pdf.PdfWorkspace
import app.dewey.pdf.ProtectFailure
import app.dewey.pdf.ProtectToolException
import app.dewey.pdf.RasterFailure
import app.dewey.pdf.RasterToolException
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Locale

class ToolTextTest {

    @Test
    fun `a page that does not exist names both the page and how many there are`() {
        val error = PageOperationException(PageOperations.Issue.PageOutOfRange(page = 9, pageCount = 4))

        assertThat(toolFailureMessage(error)).isEqualTo("There is no page 9 — this document has 4 pages.")
    }

    @Test
    fun `a one-page document is not described as having 1 pages`() {
        val error = PageOperationException(PageOperations.Issue.PageOutOfRange(page = 2, pageCount = 1))

        assertThat(toolFailureMessage(error)).endsWith("has 1 page.")
    }

    @Test
    fun `an encrypted source tells the user what to do about it`() {
        assertThat(toolFailureMessage(PdfToolException(PdfWorkspace.Failure.Encrypted("x.pdf"))))
            .contains("Unlock it first")
        assertThat(toolFailureMessage(RasterToolException(RasterFailure.Protected("x.pdf"))))
            .contains("Unlock it first")
    }

    @Test
    fun `a write failure never leaks the underlying reason onto the screen`() {
        // The reason is written for a log and can carry a provider's paths.
        val error = PdfToolException(PdfWorkspace.Failure.CouldNotWrite("EACCES /storage/emulated/0/secret"))

        assertThat(toolFailureMessage(error)).doesNotContain("storage")
    }

    @Test
    fun `password problems are specific`() {
        assertThat(toolFailureMessage(ProtectToolException(ProtectFailure.WeakPassword(PasswordCheck.Empty))))
            .isEqualTo("Enter a password.")
        assertThat(toolFailureMessage(ProtectToolException(ProtectFailure.WeakPassword(PasswordCheck.TooShort(8)))))
            .isEqualTo("Use at least 8 characters.")
        assertThat(toolFailureMessage(ProtectToolException(ProtectFailure.WrongPassword)))
            .isEqualTo("That password doesn't open this PDF.")
    }

    @Test
    fun `an unexpected exception gets a generic message, not its own text`() {
        assertThat(toolFailureMessage(IllegalStateException("internal detail")))
            .isEqualTo("Something went wrong. Nothing was changed.")
    }

    @Test
    fun `a derived name keeps the source name and says what was done`() {
        assertThat(derivedFileName("lease.pdf", "rotated")).isEqualTo("lease-rotated.pdf")
    }

    @Test
    fun `a derived name survives a source with dots, no extension, or no name`() {
        assertThat(derivedFileName("scan.2024.03.pdf", "merged")).isEqualTo("scan.2024.03-merged.pdf")
        assertThat(derivedFileName("receipt", "compressed")).isEqualTo("receipt-compressed.pdf")
        assertThat(derivedFileName("", "pages")).isEqualTo("document-pages.pdf")
        assertThat(derivedFileName("photo.pdf", "page-1", "jpg")).isEqualTo("photo-page-1.jpg")
    }

    @Test
    fun `sizes read the way a file manager shows them`() {
        Locale.setDefault(Locale.US)
        assertThat(formatBytes(0)).isEqualTo("size unknown")
        assertThat(formatBytes(512)).isEqualTo("512 B")
        assertThat(formatBytes(2048)).isEqualTo("2 KB")
        assertThat(formatBytes(5L * 1024 * 1024 + 512 * 1024)).isEqualTo("5.5 MB")
    }
}

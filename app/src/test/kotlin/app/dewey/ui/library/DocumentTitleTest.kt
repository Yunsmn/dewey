package app.dewey.ui.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * The fallback matrix for the library row's title and trailing amount - see
 * the class doc on DocumentTitle.kt for why this lives as plain functions
 * rather than only being checkable by eyeballing a running app.
 */
class DocumentTitleTest {

    @Test
    fun `returns null when there is no vendor`() {
        val title = documentTitle(vendor = null, issueDate = LocalDate.of(2023, 1, 28), dueDate = null)

        assertThat(title).isNull()
    }

    @Test
    fun `returns null when the vendor is blank`() {
        val title = documentTitle(vendor = "   ", issueDate = null, dueDate = null)

        assertThat(title).isNull()
    }

    @Test
    fun `returns just the vendor when no date was extracted`() {
        val title = documentTitle(vendor = "Lydec", issueDate = null, dueDate = null)

        assertThat(title).isEqualTo("Lydec")
    }

    @Test
    fun `combines vendor and month-year from the issue date`() {
        val title = documentTitle(
            vendor = "Lydec",
            issueDate = LocalDate.of(2023, 1, 28),
            dueDate = null,
        )

        assertThat(title).isEqualTo("Lydec · janvier 2023")
    }

    @Test
    fun `falls back to the due date when there is no issue date`() {
        // Lydec bills state only a due date - see DateFieldExtractorTest - and
        // the title is still expected to carry a month, not go dateless.
        val title = documentTitle(
            vendor = "Lydec",
            issueDate = null,
            dueDate = LocalDate.of(2023, 1, 28),
        )

        assertThat(title).isEqualTo("Lydec · janvier 2023")
    }

    @Test
    fun `prefers the issue date over the due date when both are present`() {
        val title = documentTitle(
            vendor = "Wafa Assurance",
            issueDate = LocalDate.of(2023, 3, 1),
            dueDate = LocalDate.of(2024, 3, 1),
        )

        assertThat(title).isEqualTo("Wafa Assurance · mars 2023")
    }

    @Test
    fun `uses Moroccan Arabic month names when the vendor is written in Arabic`() {
        val title = documentTitle(
            vendor = "المكتب الوطني للكهرباء",
            issueDate = LocalDate.of(2023, 1, 15),
            dueDate = null,
        )

        assertThat(title).contains("يناير 2023")
    }

    @Test
    fun `formats a null amount as no trailing text`() {
        assertThat(formatAmount(null)).isNull()
    }

    @Test
    fun `formats an amount to two decimal places with a thousands separator`() {
        assertThat(formatAmount(281.26)).isEqualTo("281.26")
        assertThat(formatAmount(4200.0)).isEqualTo("4,200.00")
    }
}

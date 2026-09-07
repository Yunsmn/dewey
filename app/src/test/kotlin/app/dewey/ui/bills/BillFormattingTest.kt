package app.dewey.ui.bills

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The bill row's due-date wording and amount formatting - see the class doc
 * on BillFormatting.kt.
 */
class BillFormattingTest {

    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun `a due date of today reads as due today`() {
        assertThat(dueDateWords(today, today)).isEqualTo("Due today")
    }

    @Test
    fun `a due date tomorrow reads as due tomorrow, not due in 1 day`() {
        assertThat(dueDateWords(today.plusDays(1), today)).isEqualTo("Due tomorrow")
    }

    @Test
    fun `a due date a few days out counts the days`() {
        assertThat(dueDateWords(today.plusDays(5), today)).isEqualTo("Due in 5 days")
    }

    @Test
    fun `exactly 14 days out still counts down rather than naming the date`() {
        assertThat(dueDateWords(today.plusDays(14), today)).isEqualTo("Due in 14 days")
    }

    @Test
    fun `a due date one day overdue is singular`() {
        assertThat(dueDateWords(today.minusDays(1), today)).isEqualTo("Overdue by 1 day")
    }

    @Test
    fun `a due date several days overdue is plural`() {
        assertThat(dueDateWords(today.minusDays(3), today)).isEqualTo("Overdue by 3 days")
    }

    @Test
    fun `a due date far in the past is still just a day count, however large`() {
        assertThat(dueDateWords(today.minusDays(400), today)).isEqualTo("Overdue by 400 days")
    }

    @Test
    fun `a due date years away names the date instead of counting days`() {
        val dueDate = LocalDate.of(2027, 1, 12)

        assertThat(dueDateWords(dueDate, today)).isEqualTo("Due 12 janvier 2027")
    }

    @Test
    fun `formats an amount with its currency`() {
        assertThat(formatBillAmount(281.26, "MAD")).isEqualTo("281.26 MAD")
    }

    @Test
    fun `formats an amount with no currency as just the number`() {
        assertThat(formatBillAmount(4200.0, null)).isEqualTo("4,200.00")
    }

    @Test
    fun `treats a blank currency the same as no currency`() {
        assertThat(formatBillAmount(4200.0, "   ")).isEqualTo("4,200.00")
    }
}

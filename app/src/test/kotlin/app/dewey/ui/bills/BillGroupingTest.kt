package app.dewey.ui.bills

import app.dewey.domain.model.Document
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The bucketing and ordering rules for the bills screen - see the class doc
 * on BillGrouping.kt for why this needs an injectable "today" rather than
 * `LocalDate.now()`.
 */
class BillGroupingTest {

    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun `returns no sections when there are no documents`() {
        val sections = groupBills(emptyList(), today)

        assertThat(sections).isEmpty()
    }

    @Test
    fun `drops a document with a due date but no amount`() {
        val document = bill(id = 1, dueDate = today.plusDays(1), amount = null)

        val sections = groupBills(listOf(document), today)

        assertThat(sections).isEmpty()
    }

    @Test
    fun `drops a document with an amount but no due date`() {
        val document = bill(id = 1, dueDate = null, amount = 50.0)

        val sections = groupBills(listOf(document), today)

        assertThat(sections).isEmpty()
    }

    @Test
    fun `a due date in the past is overdue`() {
        val document = bill(id = 1, dueDate = today.minusDays(1))

        val sections = groupBills(listOf(document), today)

        assertThat(sections).containsExactly(BillSection(BillUrgency.OVERDUE, listOf(document)))
    }

    @Test
    fun `a due date far in the past is still just overdue`() {
        val document = bill(id = 1, dueDate = today.minusYears(3))

        val sections = groupBills(listOf(document), today)

        assertThat(sections.single().urgency).isEqualTo(BillUrgency.OVERDUE)
    }

    @Test
    fun `due today counts as due soon, not overdue`() {
        val document = bill(id = 1, dueDate = today)

        val sections = groupBills(listOf(document), today)

        assertThat(sections.single().urgency).isEqualTo(BillUrgency.DUE_SOON)
    }

    @Test
    fun `exactly 14 days out is still due soon`() {
        val document = bill(id = 1, dueDate = today.plusDays(14))

        val sections = groupBills(listOf(document), today)

        assertThat(sections.single().urgency).isEqualTo(BillUrgency.DUE_SOON)
    }

    @Test
    fun `15 days out is later, not due soon`() {
        val document = bill(id = 1, dueDate = today.plusDays(15))

        val sections = groupBills(listOf(document), today)

        assertThat(sections.single().urgency).isEqualTo(BillUrgency.LATER)
    }

    @Test
    fun `a due date years away is later`() {
        val document = bill(id = 1, dueDate = today.plusYears(2))

        val sections = groupBills(listOf(document), today)

        assertThat(sections.single().urgency).isEqualTo(BillUrgency.LATER)
    }

    @Test
    fun `sections come out in Overdue, Due soon, Later order`() {
        val later = bill(id = 1, dueDate = today.plusDays(30))
        val overdue = bill(id = 2, dueDate = today.minusDays(1))
        val dueSoon = bill(id = 3, dueDate = today.plusDays(2))

        val sections = groupBills(listOf(later, overdue, dueSoon), today)

        assertThat(sections.map { it.urgency })
            .containsExactly(BillUrgency.OVERDUE, BillUrgency.DUE_SOON, BillUrgency.LATER)
            .inOrder()
    }

    @Test
    fun `omits a section with nothing in it rather than showing it empty`() {
        val document = bill(id = 1, dueDate = today.plusDays(1))

        val sections = groupBills(listOf(document), today)

        assertThat(sections.map { it.urgency }).containsExactly(BillUrgency.DUE_SOON)
    }

    @Test
    fun `orders bills within a section soonest due date first`() {
        val furthest = bill(id = 1, dueDate = today.plusDays(10))
        val soonest = bill(id = 2, dueDate = today.plusDays(1))
        val middle = bill(id = 3, dueDate = today.plusDays(5))

        val sections = groupBills(listOf(furthest, soonest, middle), today)

        assertThat(sections.single().documents.map { it.id }).containsExactly(2L, 3L, 1L).inOrder()
    }

    @Test
    fun `the most overdue bill sorts first within Overdue`() {
        val threeDaysLate = bill(id = 1, dueDate = today.minusDays(3))
        val tenDaysLate = bill(id = 2, dueDate = today.minusDays(10))

        val sections = groupBills(listOf(threeDaysLate, tenDaysLate), today)

        assertThat(sections.single().documents.map { it.id }).containsExactly(2L, 1L).inOrder()
    }

    private fun bill(id: Long, dueDate: LocalDate?, amount: Double? = 100.0): Document = Document(
        id = id,
        uri = "uri-$id",
        displayName = "doc-$id.pdf",
        sizeBytes = 0,
        lastModified = 0,
        dueDate = dueDate,
        amount = amount,
    )
}

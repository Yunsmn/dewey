package app.dewey.widgets.bills

import app.dewey.domain.model.Document
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The Bills widget's own choice of what to show - entitlement gate, empty
 * state, and the soonest-3-first cap - kept separate from BillGroupingTest
 * since that file owns the underlying bucketing rule this one only reuses.
 */
class BillsWidgetSelectionTest {

    private val today = LocalDate.of(2026, 9, 6)

    @Test
    fun `locked when not entitled, regardless of what documents say`() {
        val documents = listOf(bill(id = 1, dueDate = today.plusDays(1)))

        val content = billsWidgetContent(documents, isEntitled = false, today = today)

        assertThat(content).isEqualTo(BillsWidgetContent.Locked)
    }

    @Test
    fun `empty when entitled but there are no bills`() {
        val content = billsWidgetContent(emptyList(), isEntitled = true, today = today)

        assertThat(content).isEqualTo(BillsWidgetContent.Empty)
    }

    @Test
    fun `a document missing a due date or amount is not a bill`() {
        val documents = listOf(
            bill(id = 1, dueDate = null, amount = 50.0),
            bill(id = 2, dueDate = today.plusDays(1), amount = null),
        )

        val content = billsWidgetContent(documents, isEntitled = true, today = today)

        assertThat(content).isEqualTo(BillsWidgetContent.Empty)
    }

    @Test
    fun `caps at 3 rows but reports the true total in the count`() {
        val documents = (1..5L).map { bill(id = it, dueDate = today.plusDays(it)) }

        val content = billsWidgetContent(documents, isEntitled = true, today = today) as BillsWidgetContent.Rows

        assertThat(content.totalCount).isEqualTo(5)
        assertThat(content.bills).hasSize(3)
    }

    @Test
    fun `rows come out soonest due date first, overdue included`() {
        val later = bill(id = 1, dueDate = today.plusDays(30))
        val overdue = bill(id = 2, dueDate = today.minusDays(3))
        val dueSoon = bill(id = 3, dueDate = today.plusDays(2))

        val content = billsWidgetContent(listOf(later, overdue, dueSoon), isEntitled = true, today = today)
            as BillsWidgetContent.Rows

        assertThat(content.bills.map { it.dueWords })
            .containsExactly("Overdue by 3 days", "Due in 2 days", "Due 6 octobre 2026")
            .inOrder()
    }

    @Test
    fun `a row falls back to the filename when there is no vendor`() {
        val document = bill(id = 1, dueDate = today.plusDays(1)).copy(vendor = null, displayName = "unknown.pdf")

        val content = billsWidgetContent(listOf(document), isEntitled = true, today = today) as BillsWidgetContent.Rows

        assertThat(content.bills.single().vendor).isEqualTo("unknown.pdf")
    }

    @Test
    fun `a blank vendor also falls back to the filename`() {
        val document = bill(id = 1, dueDate = today.plusDays(1)).copy(vendor = "   ", displayName = "unknown.pdf")

        val content = billsWidgetContent(listOf(document), isEntitled = true, today = today) as BillsWidgetContent.Rows

        assertThat(content.bills.single().vendor).isEqualTo("unknown.pdf")
    }

    @Test
    fun `a row carries the vendor and formatted amount`() {
        val document = bill(id = 1, dueDate = today.plusDays(1), amount = 281.26)
            .copy(vendor = "Lydec", currency = "MAD")

        val content = billsWidgetContent(listOf(document), isEntitled = true, today = today) as BillsWidgetContent.Rows

        val row = content.bills.single()
        assertThat(row.vendor).isEqualTo("Lydec")
        assertThat(row.amount).isEqualTo("281.26 MAD")
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

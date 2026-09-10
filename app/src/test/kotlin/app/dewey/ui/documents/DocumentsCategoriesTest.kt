package app.dewey.ui.documents

import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.ui.home.CategoryCount
import app.dewey.ui.library.LibrarySection
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [categoryChips] and [documentsForCategory] drive the Documents tab's chip
 * row and the list beneath it — both read straight off the sections
 * `LibraryViewModel` already grouped, so what they do with those sections is
 * worth a JVM test on its own.
 */
class DocumentsCategoriesTest {

    private val bill = Document(1, "u1", "bill.pdf", 0, 0, docType = DocType.UTILITY_BILL)
    private val statement = Document(2, "u2", "statement.pdf", 0, 0, docType = DocType.BANK_STATEMENT)
    private val secondBill = Document(3, "u3", "bill2.pdf", 0, 0, docType = DocType.UTILITY_BILL)

    private val sections = listOf(
        LibrarySection("Bills", listOf(bill, secondBill)),
        LibrarySection("Bank", listOf(statement)),
    )

    @Test
    fun `one chip per section, in the same order, with its own count`() {
        val chips = categoryChips(sections)

        assertThat(chips).containsExactly(
            CategoryCount("Bills", 2),
            CategoryCount("Bank", 1),
        ).inOrder()
    }

    @Test
    fun `no category selected reveals every document across every section`() {
        val documents = documentsForCategory(sections, category = null)

        assertThat(documents).containsExactly(bill, secondBill, statement).inOrder()
    }

    @Test
    fun `selecting a category reveals only its own documents`() {
        val documents = documentsForCategory(sections, category = "Bills")

        assertThat(documents).containsExactly(bill, secondBill).inOrder()
    }

    @Test
    fun `a label that matches no section reveals nothing, not everything`() {
        val documents = documentsForCategory(sections, category = "Voiture")

        assertThat(documents).isEmpty()
    }
}

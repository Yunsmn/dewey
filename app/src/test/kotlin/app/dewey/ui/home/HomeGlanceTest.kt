package app.dewey.ui.home

import app.dewey.domain.model.Document
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * The Home "At a glance" windowing and ranking rules - see the class doc on
 * HomeGlance.kt for why these are plain functions rather than something only
 * checked by eyeballing the running app.
 */
class HomeGlanceTest {

    private val today = LocalDate.of(2026, 9, 10)

    @Test
    fun `no glance when nothing is due within the window`() {
        val document = bill(id = 1, dueDate = today.plusDays(45))

        assertThat(billsGlance(listOf(document), today)).isNull()
    }

    @Test
    fun `an overdue bill does not count towards the glance`() {
        val document = bill(id = 1, dueDate = today.minusDays(1))

        assertThat(billsGlance(listOf(document), today)).isNull()
    }

    @Test
    fun `a bill due today counts, at the near edge of the window`() {
        val document = bill(id = 1, dueDate = today)

        val glance = billsGlance(listOf(document), today)

        assertThat(glance?.dueSoonCount).isEqualTo(1)
        assertThat(glance?.soonest?.id).isEqualTo(1L)
    }

    @Test
    fun `exactly 30 days out still counts, at the far edge of the window`() {
        val document = bill(id = 1, dueDate = today.plusDays(30))

        assertThat(billsGlance(listOf(document), today)?.dueSoonCount).isEqualTo(1)
    }

    @Test
    fun `31 days out falls outside the window`() {
        val document = bill(id = 1, dueDate = today.plusDays(31))

        assertThat(billsGlance(listOf(document), today)).isNull()
    }

    @Test
    fun `counts every bill in the window but surfaces only the soonest`() {
        val soonest = bill(id = 1, dueDate = today.plusDays(2))
        val middle = bill(id = 2, dueDate = today.plusDays(10))
        val furthest = bill(id = 3, dueDate = today.plusDays(20))

        val glance = billsGlance(listOf(furthest, soonest, middle), today)

        assertThat(glance?.dueSoonCount).isEqualTo(3)
        assertThat(glance?.soonest?.id).isEqualTo(1L)
    }

    @Test
    fun `a document missing an amount is not a bill`() {
        val document = bill(id = 1, dueDate = today.plusDays(2), amount = null)

        assertThat(billsGlance(listOf(document), today)).isNull()
    }

    @Test
    fun `a document missing a due date is not a bill`() {
        val document = bill(id = 1, dueDate = null)

        assertThat(billsGlance(listOf(document), today)).isNull()
    }

    @Test
    fun `top categories are ordered by count, most documents first`() {
        val documents = listOf(
            filed(id = 1, folder = "Voiture"),
            filed(id = 2, folder = "Voiture"),
            filed(id = 3, folder = "Bills"),
        )

        val categories = topCategories(documents)

        assertThat(categories).containsExactly(
            CategoryCount("Voiture", 2),
            CategoryCount("Bills", 1),
        ).inOrder()
    }

    @Test
    fun `a tie between categories breaks alphabetically`() {
        val documents = listOf(filed(id = 1, folder = "Zeta"), filed(id = 2, folder = "Alpha"))

        val categories = topCategories(documents)

        assertThat(categories.map { it.label }).containsExactly("Alpha", "Zeta").inOrder()
    }

    @Test
    fun `only the busiest categories are kept`() {
        val documents = (1..6).map { filed(id = it.toLong(), folder = "Cat$it") }

        val categories = topCategories(documents, limit = 4)

        assertThat(categories).hasSize(4)
    }

    @Test
    fun `an unfiled document falls under its doc type's readable name`() {
        val document = Document(id = 1, uri = "u1", displayName = "doc.pdf", sizeBytes = 0, lastModified = 0)

        val categories = topCategories(listOf(document))

        assertThat(categories).containsExactly(CategoryCount("Unsorted", 1))
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

    private fun filed(id: Long, folder: String): Document = Document(
        id = id,
        uri = "uri-$id",
        displayName = "doc-$id.pdf",
        sizeBytes = 0,
        lastModified = 0,
        sortedFolder = folder,
    )
}

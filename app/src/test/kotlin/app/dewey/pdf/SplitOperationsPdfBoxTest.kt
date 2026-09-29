package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Test

/**
 * [SplitOperations.buildPart]'s PDFBox call against a real, in-memory
 * document — the sibling of [PageOperationsPdfBoxTest], which exists because
 * `importPage` already appends the copy it returns; wrapping it in a second
 * `addPage` put every page in twice the last time this codebase got that
 * wrong. Pages are told apart by width, page n being n × 100 points wide, so
 * order is checked as well as count.
 */
class SplitOperationsPdfBoxTest {

    private fun document(pageCount: Int): PDDocument = PDDocument().apply {
        repeat(pageCount) { index -> addPage(PDPage(PDRectangle((index + 1) * 100f, 500f))) }
    }

    private fun PDDocument.pageNumbers(): List<Int> = pages.map { (it.mediaBox.width / 100f).toInt() }

    @Test
    fun `a part holds exactly the named pages, in the order named`() {
        document(5).use { source ->
            SplitOperations.buildPart(source, SplitOperations.PageGroup(listOf(3, 0, 1))).use { part ->
                assertThat(part.pageNumbers()).containsExactly(4, 1, 2).inOrder()
            }
        }
    }

    @Test
    fun `consecutive parts of an even split cover the whole document without overlap`() {
        document(6).use { source ->
            val groups = SplitOperations.everyNPages(pageCount = 6, size = 2).getOrThrow()

            val parts = groups.map { group -> SplitOperations.buildPart(source, group) }
            try {
                assertThat(parts.map { it.pageNumbers() }).containsExactly(
                    listOf(1, 2),
                    listOf(3, 4),
                    listOf(5, 6),
                ).inOrder()
            } finally {
                parts.forEach { it.close() }
            }
        }
    }

    @Test
    fun `a ranges split produces parts sized exactly as named`() {
        document(11).use { source ->
            val groups = SplitOperations.byRanges("1-3, 4-7, 8-11", pageCount = 11).getOrThrow()

            val parts = groups.map { group -> SplitOperations.buildPart(source, group) }
            try {
                assertThat(parts.map { it.numberOfPages }).containsExactly(3, 4, 4).inOrder()
                assertThat(parts.map { it.pageNumbers() }).containsExactly(
                    listOf(1, 2, 3),
                    listOf(4, 5, 6, 7),
                    listOf(8, 9, 10, 11),
                ).inOrder()
            } finally {
                parts.forEach { it.close() }
            }
        }
    }

    @Test
    fun `building a part leaves the source document untouched`() {
        document(5).use { source ->
            SplitOperations.buildPart(source, SplitOperations.PageGroup(listOf(0, 1))).close()

            assertThat(source.pageNumbers()).containsExactly(1, 2, 3, 4, 5).inOrder()
        }
    }
}

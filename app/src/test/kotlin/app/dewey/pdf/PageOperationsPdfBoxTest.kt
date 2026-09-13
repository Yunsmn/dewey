package app.dewey.pdf

import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import org.junit.Test

/**
 * [PageOperations]' PDFBox calls against real, in-memory documents.
 *
 * Exists because merge and extract once wrapped `importPage` — which already
 * appends the page — in a second `addPage`, so every page came out twice. The
 * parsing tests could never see that; only counting the pages of an actual
 * result does. Pages are told apart by width, page n being n × 100 points
 * wide, so order is checked as well as count.
 */
class PageOperationsPdfBoxTest {

    private fun document(pageCount: Int): PDDocument = PDDocument().apply {
        repeat(pageCount) { index -> addPage(PDPage(PDRectangle((index + 1) * 100f, 500f))) }
    }

    /** Each page's number within the document it was built in, read back from its width. */
    private fun PDDocument.pageNumbers(): List<Int> = pages.map { (it.mediaBox.width / 100f).toInt() }

    @Test
    fun `extract copies each named page exactly once, in the order named`() {
        document(5).use { source ->
            PDDocument().use { into ->
                PageOperations.extract(source, "4,1-2", into).getOrThrow()

                assertThat(into.pageNumbers()).containsExactly(4, 1, 2).inOrder()
            }
        }
    }

    @Test
    fun `extract leaves the source document untouched`() {
        document(5).use { source ->
            PDDocument().use { into ->
                PageOperations.extract(source, "1-3", into).getOrThrow()

                assertThat(source.pageNumbers()).containsExactly(1, 2, 3, 4, 5).inOrder()
            }
        }
    }

    @Test
    fun `merge copies every page of every document exactly once, in order`() {
        document(2).use { first ->
            document(3).use { second ->
                PDDocument().use { into ->
                    PageOperations.merge(listOf(first, second), into).getOrThrow()

                    assertThat(into.pageNumbers()).containsExactly(1, 2, 1, 2, 3).inOrder()
                }
            }
        }
    }

    @Test
    fun `delete removes exactly the named pages`() {
        document(6).use { document ->
            PageOperations.delete(document, "2,5-6").getOrThrow()

            assertThat(document.pageNumbers()).containsExactly(1, 3, 4).inOrder()
        }
    }

    @Test
    fun `reorder moves one page and shifts the rest without adding or losing any`() {
        document(5).use { document ->
            PageOperations.reorder(document, from = 5, to = 1).getOrThrow()

            assertThat(document.pageNumbers()).containsExactly(5, 1, 2, 3, 4).inOrder()
        }
    }

    @Test
    fun `rotate turns only the named pages and keeps the page count`() {
        document(3).use { document ->
            PageOperations.rotate(document, "2", 90).getOrThrow()

            assertThat(document.pages.map { it.rotation }).containsExactly(0, 90, 0).inOrder()
        }
    }
}

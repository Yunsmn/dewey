package app.dewey.sort

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The URI-shape rule, which is where SAF quietly punishes a wrong assumption.
 *
 * A tree URI and a document URI look nearly identical and are not
 * interchangeable: `createDocument` rejects a tree URI with "Invalid URI", and
 * the obvious way to tell them apart — `DocumentsContract.isDocumentUri` —
 * throws a NullPointerException without a Context. Both cost a device round trip
 * to discover, so they are pinned here instead.
 *
 * Written against path segments rather than Uri so it needs no Android runtime.
 */
class DocumentMoverTest {

    private fun segments(vararg parts: String) = parts.toList()

    @Test
    fun `a bare tree uri needs converting`() {
        // content://auth/tree/primary:Download%2FArchive
        assertThat(DocumentMover.isTreeOnly(segments("tree", "primary:Download/Archive"))).isTrue()
    }

    @Test
    fun `a document inside a tree does not`() {
        assertThat(
            DocumentMover.isTreeOnly(
                segments("tree", "primary:Download", "document", "primary:Download/file.pdf")
            )
        ).isFalse()
    }

    @Test
    fun `a plain document uri does not`() {
        assertThat(DocumentMover.isTreeOnly(segments("document", "primary:file.pdf"))).isFalse()
    }

    @Test
    fun `an unrelated uri is not a bare tree`() {
        assertThat(DocumentMover.isTreeOnly(segments("external", "file", "1"))).isFalse()
        assertThat(DocumentMover.isTreeOnly(emptyList())).isFalse()
    }

    @Test
    fun `a tree whose document id contains the word document is still a tree`() {
        // The id is one segment, so "document" inside it is not the /document/
        // path segment that marks a document URI.
        assertThat(DocumentMover.isTreeOnly(segments("tree", "primary:Download/document"))).isTrue()
    }

    @Test
    fun `a tree segment appearing later does not count`() {
        assertThat(DocumentMover.isTreeOnly(segments("document", "primary:tree"))).isFalse()
    }
}

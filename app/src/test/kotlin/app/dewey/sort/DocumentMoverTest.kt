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

    @Test
    fun `a copy the same size as its source may be trusted`() {
        assertThat(DocumentMover.verifyCopy(sourceSize = 4_096, copySize = 4_096))
            .isEqualTo(DocumentMover.CopyCheck.MATCHES)
    }

    @Test
    fun `an empty file copied to an empty file is a good copy`() {
        // Zero is a real size, not a missing one — the distinction that keeps
        // this from being written as a null-or-zero check.
        assertThat(DocumentMover.verifyCopy(sourceSize = 0, copySize = 0))
            .isEqualTo(DocumentMover.CopyCheck.MATCHES)
    }

    @Test
    fun `a short copy is not trusted`() {
        assertThat(DocumentMover.verifyCopy(sourceSize = 4_096, copySize = 1_024))
            .isEqualTo(DocumentMover.CopyCheck.DIFFERS)
    }

    @Test
    fun `a copy that came back empty is not trusted`() {
        assertThat(DocumentMover.verifyCopy(sourceSize = 4_096, copySize = 0))
            .isEqualTo(DocumentMover.CopyCheck.DIFFERS)
    }

    @Test
    fun `a provider that reports no size leaves the copy unverifiable`() {
        assertThat(DocumentMover.verifyCopy(sourceSize = null, copySize = 4_096))
            .isEqualTo(DocumentMover.CopyCheck.UNVERIFIABLE)
        assertThat(DocumentMover.verifyCopy(sourceSize = 4_096, copySize = null))
            .isEqualTo(DocumentMover.CopyCheck.UNVERIFIABLE)
        assertThat(DocumentMover.verifyCopy(sourceSize = null, copySize = null))
            .isEqualTo(DocumentMover.CopyCheck.UNVERIFIABLE)
    }
}

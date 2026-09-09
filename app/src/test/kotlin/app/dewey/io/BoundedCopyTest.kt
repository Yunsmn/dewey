package app.dewey.io

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The bound on a spooled copy. Two callers rely on it — OCR, when a provider
 * hands back a descriptor PdfRenderer cannot seek, and the PDF raster tools,
 * for the same reason. Without it a scanned book is written into the app's
 * cache in full before anyone notices.
 */
class BoundedCopyTest {

    private fun copy(sourceBytes: Int, limit: Long): Pair<Boolean, Int> {
        val output = ByteArrayOutputStream()
        val copied = copyBounded(
            ByteArrayInputStream(ByteArray(sourceBytes) { 7 }),
            output,
            limit,
        )
        return copied to output.size()
    }

    @Test
    fun `a document under the limit is copied whole`() {
        val (copied, written) = copy(sourceBytes = 5_000, limit = 100_000)
        assertThat(copied).isTrue()
        assertThat(written).isEqualTo(5_000)
    }

    @Test
    fun `a document exactly at the limit is copied`() {
        // The limit is what fits, not what is too much.
        val (copied, written) = copy(sourceBytes = 8_192, limit = 8_192)
        assertThat(copied).isTrue()
        assertThat(written).isEqualTo(8_192)
    }

    @Test
    fun `a document over the limit is refused`() {
        val (copied, _) = copy(sourceBytes = 200_000, limit = 8_192)
        assertThat(copied).isFalse()
    }

    @Test
    fun `nothing past the limit is written to disk`() {
        // The point of the bound is the cache, so the assertion that matters is
        // how much got written, not just the return value.
        val (_, written) = copy(sourceBytes = 10_000_000, limit = 8_192)
        assertThat(written).isAtMost(8_192)
    }

    @Test
    fun `an empty document is a successful copy`() {
        val (copied, written) = copy(sourceBytes = 0, limit = 8_192)
        assertThat(copied).isTrue()
        assertThat(written).isEqualTo(0)
    }
}

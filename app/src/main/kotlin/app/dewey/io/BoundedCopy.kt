package app.dewey.io

import java.io.InputStream
import java.io.OutputStream

/**
 * Copies at most [limit] bytes, returning false if the source held more.
 *
 * Two unrelated parts of the app need this and for the same reason: both spool
 * a document into the app's cache so something that cannot read a stream can
 * read a file, and neither can afford to let a scanned book land there
 * unbounded. It lives here rather than in either of them because it is a
 * property of copying streams, not of indexing or of PDF editing — the PDF
 * tools reaching into the indexer for it, which is how this started, made the
 * tools depend on a package they have nothing to do with.
 *
 * A file exactly at the limit is copied: the limit is what fits, not what is
 * too much.
 */
fun copyBounded(input: InputStream, output: OutputStream, limit: Long): Boolean {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var written = 0L

    while (true) {
        val read = input.read(buffer)
        if (read < 0) return true
        written += read
        if (written > limit) return false
        output.write(buffer, 0, read)
    }
}

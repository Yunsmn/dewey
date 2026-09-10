package app.dewey.data.recent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RecentFilesTest {

    @Test
    fun `addRecent puts the new entry first`() {
        val current = listOf(
            RecentFile("uri:2", "File 2", RecentKind.TOOL, 200),
            RecentFile("uri:3", "File 3", RecentKind.SCAN, 300),
        )
        val entry = RecentFile("uri:1", "File 1", RecentKind.TOOL, 100)

        val result = addRecent(current, entry, max = 10)

        assertThat(result.kept.map { it.uri }).containsExactly("uri:1", "uri:2", "uri:3").inOrder()
    }

    @Test
    fun `addRecent replaces an older entry with the same uri`() {
        val current = listOf(
            RecentFile("uri:1", "File 1 Old", RecentKind.TOOL, 100),
            RecentFile("uri:2", "File 2", RecentKind.SCAN, 200),
        )
        val entry = RecentFile("uri:1", "File 1 New", RecentKind.TOOL, 150)

        val result = addRecent(current, entry, max = 10)

        assertThat(result.kept.map { it.uri }).containsExactly("uri:1", "uri:2").inOrder()
        assertThat(result.kept[0].createdAt).isEqualTo(150)
        assertThat(result.kept[0].name).isEqualTo("File 1 New")
    }

    @Test
    fun `addRecent does not duplicate when replacing`() {
        val current = listOf(
            RecentFile("uri:1", "File 1 Old", RecentKind.TOOL, 100),
            RecentFile("uri:2", "File 2", RecentKind.SCAN, 200),
        )
        val entry = RecentFile("uri:1", "File 1 New", RecentKind.TOOL, 150)

        val result = addRecent(current, entry, max = 10)

        assertThat(result.kept).hasSize(2)
    }

    @Test
    fun `addRecent caps the list at max and drops what fell off`() {
        val current = listOf(
            RecentFile("uri:2", "File 2", RecentKind.TOOL, 200),
            RecentFile("uri:3", "File 3", RecentKind.SCAN, 300),
        )
        val entry = RecentFile("uri:1", "File 1", RecentKind.TOOL, 100)

        val result = addRecent(current, entry, max = 2)

        assertThat(result.kept.map { it.uri }).containsExactly("uri:1", "uri:2").inOrder()
        assertThat(result.dropped.map { it.uri }).containsExactly("uri:3")
    }

    @Test
    fun `encodeRecent then decodeRecent round-trips a list of both kinds`() {
        val original = listOf(
            RecentFile("content://file/1", "Scan.pdf", RecentKind.SCAN, 1000),
            RecentFile("content://file/2", "Merged.pdf", RecentKind.TOOL, 2000),
            RecentFile("content://file/3", "Rotated.pdf", RecentKind.TOOL, 3000),
        )

        val encoded = encodeRecent(original)
        val decoded = decodeRecent(encoded)

        assertThat(decoded).isEqualTo(original)
    }

    @Test
    fun `encodeRecent flattens tabs and newlines in names to spaces`() {
        val original = listOf(
            RecentFile("uri:1", "File\twith\ttabs", RecentKind.TOOL, 100),
            RecentFile("uri:2", "File\nwith\nnewlines", RecentKind.SCAN, 200),
            RecentFile("uri:3", "File\rwith\rcarriage\rreturns", RecentKind.TOOL, 300),
        )

        val encoded = encodeRecent(original)
        val decoded = decodeRecent(encoded)

        assertThat(decoded[0].name).isEqualTo("File with tabs")
        assertThat(decoded[1].name).isEqualTo("File with newlines")
        assertThat(decoded[2].name).isEqualTo("File with carriage returns")
        assertThat(decoded).hasSize(3)
    }

    @Test
    fun `decodeRecent skips lines with wrong field count`() {
        val encoded = """
            1000	TOOL	uri:1	File 1
            1001	TOOL	uri:2
            1002	TOOL	uri:3	File 3
        """.trimIndent()

        val decoded = decodeRecent(encoded)

        assertThat(decoded.map { it.uri }).containsExactly("uri:1", "uri:3").inOrder()
    }

    @Test
    fun `decodeRecent skips lines with non-numeric timestamp`() {
        val encoded = """
            1000	TOOL	uri:1	File 1
            notanumber	TOOL	uri:2	File 2
            1002	TOOL	uri:3	File 3
        """.trimIndent()

        val decoded = decodeRecent(encoded)

        assertThat(decoded.map { it.uri }).containsExactly("uri:1", "uri:3").inOrder()
    }

    @Test
    fun `decodeRecent skips lines with unknown kind`() {
        val encoded = """
            1000	TOOL	uri:1	File 1
            1001	UNKNOWN	uri:2	File 2
            1002	SCAN	uri:3	File 3
        """.trimIndent()

        val decoded = decodeRecent(encoded)

        assertThat(decoded.map { it.uri }).containsExactly("uri:1", "uri:3").inOrder()
    }

    @Test
    fun `decodeRecent skips lines with blank uri`() {
        val encoded = """
            1000	TOOL	uri:1	File 1
            1001	TOOL		File 2
            1002	TOOL	uri:3	File 3
        """.trimIndent()

        val decoded = decodeRecent(encoded)

        assertThat(decoded.map { it.uri }).containsExactly("uri:1", "uri:3").inOrder()
    }

    @Test
    fun `decodeRecent keeps valid entries while skipping malformed ones`() {
        val encoded = """
            1000	TOOL	uri:1	File 1
            invalid line
            1002	SCAN	uri:3	File 3

            1004	TOOL	uri:5	File 5
        """.trimIndent()

        val decoded = decodeRecent(encoded)

        assertThat(decoded.map { it.uri }).containsExactly("uri:1", "uri:3", "uri:5").inOrder()
    }

    @Test
    fun `decodeRecent returns empty list for empty string`() {
        val decoded = decodeRecent("")

        assertThat(decoded).isEmpty()
    }
}

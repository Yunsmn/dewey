package app.dewey.work

import app.dewey.data.storage.SafDocument
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import android.net.Uri

/**
 * The training data for a learned category: the documents a person already put
 * in each of their own folders.
 */
class ExamplesByFolderTest {

    private fun uri(value: String): Uri = mockk<Uri>(relaxed = true).also {
        every { it.toString() } returns value
    }

    private fun folder(id: String, name: String) = SafDocument(
        uri = uri("content://tree/root/document/$id"),
        documentId = id,
        parentDocumentId = "root",
        parentUri = uri("content://tree/root"),
        displayName = name,
        mimeType = "vnd.android.document/directory",
        sizeBytes = 0,
        lastModified = 0,
    )

    private fun document(name: String, parent: String) = SafDocument(
        uri = uri("content://doc/$name"),
        documentId = name,
        parentDocumentId = parent,
        parentUri = uri("content://tree/root/document/$parent"),
        displayName = name,
        mimeType = "application/pdf",
        sizeBytes = 1,
        lastModified = 0,
    )

    private fun vector(value: Float) = floatArrayOf(value, 0f)

    @Test
    fun `documents are grouped under the folder they sit in`() {
        val folders = listOf(folder("f1", "Voiture"), folder("f2", "Bills"))
        val documents = listOf(
            document("a", "f1"), document("b", "f1"), document("c", "f2"),
        )
        val vectors = mapOf(
            "content://doc/a" to vector(1f),
            "content://doc/b" to vector(2f),
            "content://doc/c" to vector(3f),
        )

        val grouped = examplesByFolder(documents, folders, vectors)

        assertThat(grouped.keys).containsExactly("Voiture", "Bills")
        assertThat(grouped.getValue("Voiture")).hasSize(2)
        assertThat(grouped.getValue("Bills")).hasSize(1)
    }

    @Test
    fun `a document still at the tree root teaches nothing`() {
        // It has not been filed, so it is not evidence of where things go.
        val grouped = examplesByFolder(
            listOf(document("a", "root")),
            listOf(folder("f1", "Voiture")),
            mapOf("content://doc/a" to vector(1f)),
        )

        assertThat(grouped).isEmpty()
    }

    @Test
    fun `a document with no embedding is skipped, not fatal to its folder`() {
        // Indexed before chunks were written, or never indexed. Costs that one
        // example rather than the whole folder.
        val folders = listOf(folder("f1", "Voiture"))
        val documents = listOf(document("a", "f1"), document("b", "f1"))

        val grouped = examplesByFolder(
            documents, folders, mapOf("content://doc/b" to vector(2f)),
        )

        assertThat(grouped.getValue("Voiture")).hasSize(1)
    }

    @Test
    fun `documents in a nested folder we did not list are ignored`() {
        // topLevelFolders is the only thing that defines a category. A file two
        // levels down belongs to whatever its own parent is, and that is not
        // one of the folders being learned from.
        val grouped = examplesByFolder(
            listOf(document("a", "deep")),
            listOf(folder("f1", "Voiture")),
            mapOf("content://doc/a" to vector(1f)),
        )

        assertThat(grouped).isEmpty()
    }

    @Test
    fun `no folders means nothing to learn from`() {
        val grouped = examplesByFolder(listOf(document("a", "f1")), emptyList(), emptyMap())

        assertThat(grouped).isEmpty()
    }
}

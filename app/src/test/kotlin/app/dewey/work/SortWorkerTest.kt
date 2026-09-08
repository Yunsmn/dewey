package app.dewey.work

import android.net.Uri
import app.dewey.data.storage.SafDocument
import app.dewey.domain.model.DocType
import app.dewey.work.SortWorker.Companion.folderName
import app.dewey.sort.DocumentMover
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test

/**
 * The two rules a second sort and an undo both depend on: recognising a
 * document that is already filed, and remembering where a nested document
 * actually came from.
 *
 * Kept to plain data plus mockk'd [Uri] tokens rather than a running
 * [SortWorker]: this project's unit tests do not use Robolectric (see
 * DocumentMoverTest's own note on the same problem), and a real [Uri]'s
 * equals, hashCode and toString all throw "not mocked" outside one. mockk
 * never calls through to that real implementation, so a mock is the only way
 * to hold a [Uri] value here at all — these tests only ever compare mocked
 * instances against each other, never against a real one.
 */
class SortWorkerTest {

    private fun uri(label: String): Uri {
        val mock = mockk<Uri>(relaxed = true)
        every { mock.toString() } returns label
        return mock
    }

    private fun folder(documentId: String, displayName: String) = SafDocument(
        uri = uri("content://tree/root/document/$documentId"),
        documentId = documentId,
        parentDocumentId = "root",
        parentUri = uri("content://tree/root"),
        displayName = displayName,
        mimeType = "vnd.android.document/directory",
        sizeBytes = 0L,
        lastModified = 0L,
    )

    private fun document(
        documentId: String = "doc",
        parentDocumentId: String = "root",
        parentUri: Uri = uri("content://tree/root"),
        displayName: String = "file.pdf",
    ) = SafDocument(
        uri = uri("content://tree/root/document/$documentId"),
        documentId = documentId,
        parentDocumentId = parentDocumentId,
        parentUri = parentUri,
        displayName = displayName,
        mimeType = "application/pdf",
        sizeBytes = 1_000L,
        lastModified = 0L,
    )

    @Test
    fun `recognises only the top-level folders that are the app's own categories`() {
        val folders = listOf(
            folder("f1", "Bills"),
            folder("f2", "some unrelated folder the user already had"),
            folder("f3", "Bank"),
        )

        assertThat(alreadyFiledFolders(folders))
            .containsExactly("f1", DocType.UTILITY_BILL, "f3", DocType.BANK_STATEMENT)
    }

    @Test
    fun `no category folders yet means nothing is already filed`() {
        assertThat(alreadyFiledFolders(emptyList())).isEmpty()
    }

    @Test
    fun `the Unsorted folder is not a category`() {
        // Its whole meaning is "nothing is known about this". Treating it as an
        // answer would mark those documents filed and stop the next sort ever
        // looking at them again.
        assertThat(SortWorker.typeForFolderName("Unsorted")).isNull()
        assertThat(alreadyFiledFolders(listOf(folder("f1", "Unsorted")))).isEmpty()
    }

    @Test
    fun `every category folder name maps back to the type that produced it`() {
        // folderName() and typeForFolderName() are inverses, or a document
        // filed under one name comes back as a different kind of thing.
        val roundTripped = DocType.entries
            .filter { it != DocType.UNKNOWN }
            .associateWith { SortWorker.typeForFolderName(it.folderName()) }

        assertThat(roundTripped).containsExactlyEntriesIn(
            DocType.entries.filter { it != DocType.UNKNOWN }.associateWith { it }
        )
    }

    @Test
    fun `a document sitting directly inside a category folder is filed under that folder's type`() {
        val billsId = "bills-folder-id"
        val documentInBills = document(parentDocumentId = billsId)

        assertThat(documentInBills.filedUnder(mapOf(billsId to DocType.UTILITY_BILL)))
            .isEqualTo(DocType.UTILITY_BILL)
    }

    @Test
    fun `a document still at the tree root is not already filed`() {
        val documentAtRoot = document(parentDocumentId = "root")

        assertThat(documentAtRoot.filedUnder(mapOf("bills-folder-id" to DocType.UTILITY_BILL)))
            .isNull()
    }

    @Test
    fun `a second sort over an already-sorted layout recognises what each document is`() {
        // findPdfs() walks into Bills and Bank on a rerun. Those documents must
        // not be counted toward review — and, the part that was missing, the
        // folder each one sits in has to be read back as its type. Without that
        // a reinstall over a sorted folder leaves every file under "Unsorted",
        // because the database was wiped while the folders on disk were not.
        val categories = alreadyFiledFolders(
            listOf(folder("bills-id", "Bills"), folder("bank-id", "Bank")),
        )
        val secondPassDocuments = listOf(
            document(documentId = "1", parentDocumentId = "bills-id"),
            document(documentId = "2", parentDocumentId = "bank-id"),
        )

        assertThat(secondPassDocuments.map { it.filedUnder(categories) })
            .containsExactly(DocType.UTILITY_BILL, DocType.BANK_STATEMENT)
            .inOrder()
    }

    @Test
    fun `the undo record keeps the document's own parent, not the tree root`() {
        // Defect #3: a document that started inside a subfolder must be put
        // back there by undo, not dumped at the top level of the tree.
        val treeRoot = uri("content://tree/root")
        val nestedParent = uri("content://tree/root/document/statements-2023")
        val nestedDocument = document(documentId = "doc-1", parentUri = nestedParent, displayName = "statement.pdf")
        val targetParent = uri("content://tree/root/document/Bank")
        val outcome = DocumentMover.Outcome.Moved(
            from = nestedDocument.uri,
            to = uri("content://tree/root/document/Bank/statement.pdf"),
            folder = "Bank",
        )

        val record = nestedDocument.undoRecord(outcome, targetParent, "Bank")

        assertThat(record.originalParentUri).isEqualTo(nestedParent.toString())
        assertThat(record.originalParentUri).isNotEqualTo(treeRoot.toString())
    }

    @Test
    fun `the undo record carries the move's own from and to uris`() {
        val movedDocument = document(documentId = "doc-1")
        val outcome = DocumentMover.Outcome.Moved(
            from = movedDocument.uri,
            to = uri("content://tree/root/document/Bills/doc-1"),
            folder = "Bills",
        )
        val targetParent = uri("content://tree/root/document/Bills")

        val record = movedDocument.undoRecord(outcome, targetParent, "Bills")

        assertThat(record.documentUri).isEqualTo(outcome.from.toString())
        assertThat(record.movedToUri).isEqualTo(outcome.to.toString())
        assertThat(record.targetParentUri).isEqualTo(targetParent.toString())
        assertThat(record.folderName).isEqualTo("Bills")
    }
}

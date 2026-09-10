package app.dewey.ui.notes

import app.dewey.domain.model.Document
import app.dewey.domain.model.Note
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * [notePreview], [attachedBillLabel], [billPickerLabel] and
 * [noteCountsByBill] - see NoteFormatting.kt's class doc.
 */
class NoteFormattingTest {

    @Test
    fun `a short body is returned unchanged`() {
        assertThat(notePreview("Buy milk")).isEqualTo("Buy milk")
    }

    @Test
    fun `newlines collapse to a single space`() {
        assertThat(notePreview("Buy milk\nAnd eggs\n\nAnd bread")).isEqualTo("Buy milk And eggs And bread")
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        assertThat(notePreview("  Buy milk  ")).isEqualTo("Buy milk")
    }

    @Test
    fun `a body longer than the limit is cut with an ellipsis`() {
        val body = "a".repeat(200)

        val preview = notePreview(body, maxLength = 10)

        assertThat(preview).isEqualTo("a".repeat(10) + "…")
    }

    @Test
    fun `a body exactly at the limit is not truncated`() {
        val body = "a".repeat(10)

        assertThat(notePreview(body, maxLength = 10)).isEqualTo(body)
    }

    @Test
    fun `an empty body previews as empty`() {
        assertThat(notePreview("")).isEqualTo("")
    }

    @Test
    fun `attached bill label combines vendor and amount`() {
        val bill = document(vendor = "Lydec", amount = 281.26, currency = "MAD")

        assertThat(attachedBillLabel(bill)).isEqualTo("Lydec · 281.26 MAD")
    }

    @Test
    fun `attached bill label falls back to the filename when there is no vendor`() {
        val bill = document(vendor = null, amount = 4200.0, currency = null, displayName = "IMG_8262.pdf")

        assertThat(attachedBillLabel(bill)).isEqualTo("IMG_8262.pdf · 4,200.00")
    }

    @Test
    fun `attached bill label is just the name when there is no amount`() {
        val bill = document(vendor = "Amendis", amount = null, currency = null)

        assertThat(attachedBillLabel(bill)).isEqualTo("Amendis")
    }

    @Test
    fun `a blank vendor is treated the same as no vendor`() {
        val bill = document(vendor = "   ", amount = 10.0, currency = null, displayName = "doc.pdf")

        assertThat(attachedBillLabel(bill)).isEqualTo("doc.pdf · 10.00")
    }

    @Test
    fun `bill picker label includes the due date when present`() {
        val bill = document(vendor = "Lydec", amount = 281.26, currency = "MAD", dueDate = java.time.LocalDate.of(2027, 1, 12))

        assertThat(billPickerLabel(bill)).isEqualTo("Lydec · 281.26 MAD · due 12 Jan 2027")
    }

    @Test
    fun `bill picker label omits a missing due date rather than a stray separator`() {
        val bill = document(vendor = "Lydec", amount = 281.26, currency = "MAD", dueDate = null)

        assertThat(billPickerLabel(bill)).isEqualTo("Lydec · 281.26 MAD")
    }

    @Test
    fun `note counts by bill only count notes that are attached`() {
        val notes = listOf(
            note(id = 1, billDocumentId = 1L),
            note(id = 2, billDocumentId = 1L),
            note(id = 3, billDocumentId = 2L),
            note(id = 4, billDocumentId = null),
        )

        val counts = noteCountsByBill(notes)

        assertThat(counts).containsExactlyEntriesIn(mapOf(1L to 2, 2L to 1))
    }

    @Test
    fun `note counts by bill is empty when every note is standalone`() {
        val notes = listOf(note(id = 1, billDocumentId = null))

        assertThat(noteCountsByBill(notes)).isEmpty()
    }

    private fun document(
        vendor: String?,
        amount: Double?,
        currency: String?,
        displayName: String = "doc.pdf",
        dueDate: java.time.LocalDate? = null,
    ): Document = Document(
        id = 1,
        uri = "uri",
        displayName = displayName,
        sizeBytes = 0,
        lastModified = 0,
        vendor = vendor,
        amount = amount,
        currency = currency,
        dueDate = dueDate,
    )

    private fun note(id: Long, billDocumentId: Long?): Note =
        Note(id = id, title = "t$id", body = "b$id", billDocumentId = billDocumentId)
}

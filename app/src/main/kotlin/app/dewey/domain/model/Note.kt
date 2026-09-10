package app.dewey.domain.model

/**
 * A note the user wrote, standalone or attached to a bill.
 *
 * [billDocumentId] names a `documents` row - always a bill in today's UI,
 * since the editor's picker only ever offers bills, but the schema underneath
 * (see app.dewey.data.db.NoteRow) allows any document so a note is never
 * orphaned by what the current screen happens to expose.
 */
data class Note(
    val id: Long = 0,
    val title: String,
    val body: String,
    val billDocumentId: Long? = null,
    val pinned: Boolean = false,
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
) {
    val isStandalone: Boolean get() = billDocumentId == null
}

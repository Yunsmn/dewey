package app.dewey.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.repository.NotesRepository
import app.dewey.di.AppContainer
import app.dewey.domain.model.Document
import app.dewey.domain.model.Note
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Notes and, alongside them, the bills they can be attached to.
 *
 * Holds both flows at once rather than letting the Bills segment read from
 * its own [app.dewey.ui.bills.BillsViewModel] in isolation, because the Bills
 * segment needs to show a note count per bill - a thing [DocumentRepository]
 * has no reason to know about.
 */
class NotesViewModel(
    private val notesRepository: NotesRepository,
    documentRepository: DocumentRepository,
) : ViewModel() {

    private val editor = MutableStateFlow<NoteEditorState?>(null)

    val state: StateFlow<NotesUiState> = combine(
        notesRepository.observeNotes(),
        documentRepository.observeBills(),
        editor,
    ) { notes, bills, editorState -> NotesUiState(notes = notes, bills = bills, editor = editorState) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotesUiState(),
        )

    /** Opens the editor on a new, unsaved note - pre-attached to [attachTo] when given. */
    fun openNewNote(attachTo: Document? = null) {
        editor.value = NoteEditorState(billDocumentId = attachTo?.id)
    }

    fun openNote(note: Note) {
        editor.value = NoteEditorState(
            noteId = note.id,
            title = note.title,
            body = note.body,
            billDocumentId = note.billDocumentId,
            pinned = note.pinned,
            createdAt = note.createdAt,
        )
    }

    fun updateTitle(title: String) = editor.update { it?.copy(title = title) }

    fun updateBody(body: String) = editor.update { it?.copy(body = body) }

    fun togglePin() = editor.update { it?.copy(pinned = !it.pinned) }

    fun setBillPickerOpen(open: Boolean) = editor.update { it?.copy(pickingBill = open) }

    fun attachToBill(documentId: Long?) =
        editor.update { it?.copy(billDocumentId = documentId, pickingBill = false) }

    fun requestDelete() = editor.update { it?.copy(confirmingDelete = true) }

    fun cancelDelete() = editor.update { it?.copy(confirmingDelete = false) }

    fun confirmDelete() {
        val id = editor.value?.noteId
        editor.value = null
        if (id != null) {
            viewModelScope.launch { notesRepository.delete(id) }
        }
    }

    /**
     * Saves the current draft and closes the editor - a blank, never-saved
     * note is dropped rather than written, so opening the editor and backing
     * straight out never leaves an empty row behind.
     */
    fun saveAndClose() {
        val draft = editor.value ?: return
        editor.value = null
        if (draft.isNew && draft.title.isBlank() && draft.body.isBlank()) return

        viewModelScope.launch {
            notesRepository.save(
                Note(
                    id = draft.noteId ?: 0,
                    title = draft.title.trim(),
                    body = draft.body,
                    billDocumentId = draft.billDocumentId,
                    pinned = draft.pinned,
                    createdAt = draft.createdAt,
                )
            )
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NotesViewModel(
                    notesRepository = container.notesRepository,
                    documentRepository = container.documentRepository,
                ) as T
        }
    }
}

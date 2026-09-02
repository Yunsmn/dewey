package app.dewey.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.storage.DocumentTreeStore
import app.dewey.di.AppContainer
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.work.DeweyTask
import app.dewey.work.TaskRunner
import app.dewey.work.TaskState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibrarySection(val type: DocType, val documents: List<Document>)

data class LibraryUiState(
    val sections: List<LibrarySection> = emptyList(),
    val totalDocuments: Int = 0,
    val grantedFolders: Int = 0,
    val task: TaskState = TaskState.Idle,
) {
    val isEmpty: Boolean get() = totalDocuments == 0 && task !is TaskState.Running
}

/**
 * State for the library screen.
 *
 * Note what this does not do: it does not own the indexing job. It asks
 * [TaskRunner] to start one and observes the result. The screen can be destroyed
 * and recreated, or the app backgrounded entirely, and a four-hundred-file
 * import carries on regardless.
 */
class LibraryViewModel(
    private val repository: DocumentRepository,
    private val treeStore: DocumentTreeStore,
    private val taskRunner: TaskRunner,
) : ViewModel() {

    val state: StateFlow<LibraryUiState> = combine(
        repository.observeDocuments(),
        treeStore.grantedTrees,
        taskRunner.observe(DeweyTask.INDEX),
    ) { documents, trees, task ->
        LibraryUiState(
            sections = documents.groupBy(Document::docType)
                .map { (type, docs) -> LibrarySection(type, docs) }
                .sortedWith(compareByDescending<LibrarySection> { it.documents.size }.thenBy { it.type.name }),
            totalDocuments = documents.size,
            grantedFolders = trees.size,
            task = task,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the flow alive briefly across a rotation so the list does not
        // reload from the database every time the device turns.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun onFolderGranted(treeUri: Uri) {
        viewModelScope.launch {
            // Indexing only starts once the grant is persisted. Starting first
            // would race the worker against a permission it may not yet hold.
            if (treeStore.remember(treeUri)) {
                taskRunner.startIndexing(treeUri)
            }
        }
    }

    fun onCancelIndexing() = taskRunner.cancel(DeweyTask.INDEX)

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(
                    repository = container.documentRepository,
                    treeStore = container.documentTreeStore,
                    taskRunner = container.taskRunner,
                ) as T
        }
    }
}

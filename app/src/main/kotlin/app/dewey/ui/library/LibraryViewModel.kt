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
import app.dewey.sort.UndoLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * One heading in the library.
 *
 * Named by [label] rather than by a [DocType], because a category learned from
 * the user's own folder has no DocType — "Voiture" is a real category with a
 * real folder behind it and no enum case anywhere. Grouping by the folder makes
 * the library agree with the file manager, which is the point of the whole app.
 */
data class LibrarySection(val label: String, val documents: List<Document>)

data class LibraryUiState(
    val sections: List<LibrarySection> = emptyList(),
    val totalDocuments: Int = 0,
    val grantedFolders: Int = 0,
    val task: TaskState = TaskState.Idle,
    val sortTask: TaskState = TaskState.Idle,
    /** The one of the two the banner shows — see [bannerTask]. */
    val banner: TaskState = TaskState.Idle,
    /** Documents the classifier declined to file. Ordered least confident first. */
    val needsReview: List<Document> = emptyList(),
    /** True while the last sort can still be put back. */
    val canUndo: Boolean = false,
) {
    val isEmpty: Boolean get() = totalDocuments == 0 && task !is TaskState.Running

    val isBusy: Boolean
        get() = task is TaskState.Running || sortTask is TaskState.Running

    /** Sorting only means anything once documents have been read. */
    val canSort: Boolean get() = totalDocuments > 0 && grantedFolders > 0 && !isBusy
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
    private val undoLog: UndoLog,
) : ViewModel() {

    private val undoAvailable = MutableStateFlow(false)

    /**
     * Which task settled most recently, so the one banner shows the newer news.
     * Null until something finishes while this screen is alive — see [bannerTask].
     */
    private val lastSettled = MutableStateFlow<BannerSource?>(null)

    val state: StateFlow<LibraryUiState> = combine(
        repository.observeDocuments(),
        repository.observeNeedingReview(),
        treeStore.grantedTrees,
        taskRunner.observe(DeweyTask.INDEX),
        combine(taskRunner.observe(DeweyTask.SORT), undoAvailable) { sort, undo -> sort to undo },
    ) { documents, review, trees, indexTask, (sortTask, undo) ->
        // Review documents are listed separately rather than inside their
        // section: burying the three files that need a decision among three
        // hundred that do not is the same as not surfacing them.
        val reviewIds = review.mapTo(HashSet()) { it.id }
        val filed = documents.filterNot { it.id in reviewIds }

        recordSettled(BannerSource.INDEX, indexTask)
        recordSettled(BannerSource.SORT, sortTask)

        LibraryUiState(
            sections = filed.groupBy { it.categoryLabel(unfiled = it.docType.readable()) }
                .map { (label, docs) -> LibrarySection(label, docs) }
                .sortedWith(compareByDescending<LibrarySection> { it.documents.size }.thenBy { it.label }),
            totalDocuments = documents.size,
            grantedFolders = trees.size,
            task = indexTask,
            sortTask = sortTask,
            banner = bannerTask(indexTask, sortTask, lastSettled.value),
            needsReview = review,
            canUndo = undo,
        )
    }.stateIn(
        scope = viewModelScope,
        // Keeps the flow alive briefly across a rotation so the list does not
        // reload from the database every time the device turns.
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    /**
     * Notes that [state] has finished, if this is the emission where it did.
     *
     * Compared against what was seen last rather than set unconditionally: the
     * flow re-emits whenever the document list changes, and a settled task that
     * simply came round again is not news.
     */
    private fun recordSettled(source: BannerSource, state: TaskState) {
        if (!state.isTerminal) return
        if (seenSettled[source] == state) return
        seenSettled[source] = state
        lastSettled.value = source
    }

    private val seenSettled = HashMap<BannerSource, TaskState>()

    fun onFolderGranted(treeUri: Uri) {
        viewModelScope.launch {
            // Indexing only starts once the grant is persisted. Starting first
            // would race the worker against a permission it may not yet hold.
            if (treeStore.remember(treeUri)) {
                taskRunner.startIndexing(treeUri)
            }
        }
    }

    fun onCancelIndexing() {
        taskRunner.cancel(DeweyTask.INDEX)
        taskRunner.cancel(DeweyTask.SORT)
    }

    fun onSort() {
        viewModelScope.launch {
            val tree = treeStore.grantedTrees.first().firstOrNull() ?: return@launch
            taskRunner.startSort(tree)
        }
    }

    fun onUndo() {
        taskRunner.startUndo()
        undoAvailable.value = false
    }

    init {
        // Whether a previous sort is still undoable outlives this ViewModel, so
        // it is read from the log rather than assumed false on every launch.
        viewModelScope.launch { undoAvailable.value = undoLog.latest() != null }

        // A sort that files nothing — an empty folder, or a rerun over an
        // already-sorted one — writes nothing to the undo log, so "undo" must
        // not be offered just because a sort started. Re-reading the log once
        // the task settles is the only way the button tracks reality rather
        // than optimism.
        viewModelScope.launch {
            taskRunner.observe(DeweyTask.SORT).collect { sortTask ->
                if (sortTask.isTerminal) {
                    undoAvailable.value = undoLog.latest() != null
                }
            }
        }
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                LibraryViewModel(
                    repository = container.documentRepository,
                    treeStore = container.documentTreeStore,
                    taskRunner = container.taskRunner,
                    undoLog = container.undoLog,
                ) as T
        }
    }
}

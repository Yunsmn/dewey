package app.dewey.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.data.repository.DocumentRepository
import app.dewey.di.AppContainer
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
import app.dewey.search.SearchResult
import app.dewey.search.SearchUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class SearchViewModel(
    private val repository: DocumentRepository,
    private val search: DocumentSearch,
    private val embedderProvider: () -> Embedder,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var running: Job? = null

    fun onQueryChanged(value: String) {
        _query.value = value

        // Cancelling the previous search is what keeps typing responsive: without
        // it every keystroke queues another embedding pass and the results lag
        // several characters behind the box.
        running?.cancel()

        if (value.isBlank()) {
            _state.value = SearchUiState.Idle
            return
        }

        running = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            runSearch(value)
        }
    }

    fun onSubmit() {
        val value = _query.value
        if (value.isBlank()) return
        running?.cancel()
        running = viewModelScope.launch { runSearch(value) }
    }

    private suspend fun runSearch(text: String) {
        try {
            // First use unpacks and loads the encoder, which takes long enough
            // that saying so is better than an unexplained pause.
            val embedder = withContext(Dispatchers.IO) {
                _state.value = SearchUiState.Preparing
                embedderProvider()
            }

            _state.value = SearchUiState.Searching
            val vector = embedder.embedQuery(text)
            val hits = search.search(queryText = text, queryVector = vector, limit = HIT_LIMIT)

            // Several chunks of one document can all match. The user wants the
            // document once, represented by its strongest passage.
            val best = hits.groupBy { it.documentId }
                .mapNotNull { (documentId, group) ->
                    val strongest = group.maxBy { it.score }
                    repository.byId(documentId)?.let { document ->
                        SearchResult(document, strongest.text.trim(), strongest.score)
                    }
                }
                .sortedByDescending { it.score }
                .take(RESULT_LIMIT)

            _state.value = SearchUiState.Results(text, best)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = SearchUiState.Failed(e.message ?: "Search failed")
        }
    }

    companion object {
        /**
         * Long enough that a typed word does not trigger four searches, short
         * enough to feel immediate. Embedding a query is ~60ms, so this
         * dominates the perceived latency and is the number worth tuning.
         */
        private const val DEBOUNCE_MILLIS = 220L

        /** Chunks considered, before collapsing to one row per document. */
        private const val HIT_LIMIT = 40
        private const val RESULT_LIMIT = 12

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SearchViewModel(
                    repository = container.documentRepository,
                    search = container.documentSearch,
                    embedderProvider = { container.embedder },
                ) as T
        }
    }
}

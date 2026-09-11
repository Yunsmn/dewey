package app.dewey.ui.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.assistant.AssistantReply
import app.dewey.assistant.DocumentAssistant
import app.dewey.di.AppContainer
import app.dewey.domain.model.Document
import app.dewey.index.DocumentSearch
import app.dewey.index.Embedder
import app.dewey.search.SearchResult
import app.dewey.search.SearchUiState
import app.dewey.ui.search.bestPassagePerDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What asking a question about the linked folder is doing right now.
 *
 * Kept apart from [SearchUiState]: typing already drives a live search over
 * the same text box, and the two run independently — asking is one deliberate
 * tap further, not something every keystroke should pay for.
 */
sealed interface AnswerUiState {

    data object Idle : AnswerUiState

    /** The embedder is unpacking on first use — see [Embedder]. */
    data object Preparing : AnswerUiState

    data object Thinking : AnswerUiState

    data class Answered(val text: String, val sources: List<Document>) : AnswerUiState

    data class Failed(val message: String) : AnswerUiState
}

/**
 * Drives the Documents tab's ask bar.
 *
 * Two things share one text field and run independently: [onQueryChanged]
 * debounces into the same search-as-you-type retrieval
 * [app.dewey.ui.search.SearchViewModel] uses, and [onAsk] is the deliberate
 * next step, handed off entirely to [assistant] — see [DocumentAssistant] for
 * the retrieval-and-answer pipeline that used to live in this class, and for
 * why it also gates the shared daily question cap. [search] and
 * [resolveDocument] are the exact calls `container.documentSearch.search` and
 * `container.documentRepository.byId` make (see [factory]) — they stay here,
 * separately from [assistant], purely for [runSearch]'s own search-as-you-type
 * results. [embedderProvider] is a provider rather than an [Embedder] for the
 * same reason `SearchViewModel` takes one: constructing it unpacks a
 * hundred-megabyte model the free tier must never pay for just by opening
 * this screen.
 *
 * [io] is where both the debounced search and the ask both run — overridable
 * so a test can supply a [kotlinx.coroutines.test.TestDispatcher] tied to its
 * own virtual clock, the same reason `ScanViewModel` takes one for [io].
 */
class AskViewModel(
    private val assistant: DocumentAssistant,
    private val embedderProvider: () -> Embedder,
    private val search: suspend (queryText: String, queryVector: FloatArray, limit: Int) -> List<DocumentSearch.Hit>,
    private val resolveDocument: suspend (Long) -> Document?,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _searchState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _answerState = MutableStateFlow<AnswerUiState>(AnswerUiState.Idle)
    val answerState: StateFlow<AnswerUiState> = _answerState.asStateFlow()

    private var searchJob: Job? = null
    private var askJob: Job? = null

    fun onQueryChanged(value: String) {
        _query.value = value

        // Cancelling both is what keeps typing responsive and stops a stale
        // answer landing for a question that has since changed underneath it.
        searchJob?.cancel()
        askJob?.cancel()

        if (value.isBlank()) {
            // Clearing the field clears both results and the answer — neither
            // means anything about a question nobody is asking any more.
            _searchState.value = SearchUiState.Idle
            _answerState.value = AnswerUiState.Idle
            return
        }

        searchJob = viewModelScope.launch(io) {
            delay(DEBOUNCE_MILLIS)
            runSearch(value)
        }
    }

    /** The Ask pill, or the field's own IME action. */
    fun onAsk() {
        val question = _query.value
        if (question.isBlank()) return
        searchJob?.cancel()
        askJob?.cancel()
        askJob = viewModelScope.launch(io) { runAsk(question) }
    }

    private suspend fun runSearch(text: String) {
        try {
            _searchState.value = SearchUiState.Preparing
            val embedder = embedderProvider()
            _searchState.value = SearchUiState.Searching
            val vector = embedder.embedQuery(text)
            val hits = search(text, vector, HIT_LIMIT)

            val results = bestPassagePerDocument(hits)
                .mapNotNull { hit ->
                    resolveDocument(hit.documentId)?.let { document ->
                        SearchResult(document, hit.text.trim(), hit.score)
                    }
                }
                .take(RESULT_LIMIT)

            _searchState.value = SearchUiState.Results(text, results)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _searchState.value = SearchUiState.Failed(e.message ?: "Search failed")
        }
    }

    private suspend fun runAsk(question: String) {
        _answerState.value = when (val reply = assistant.ask(
            question = question,
            onPreparing = { _answerState.value = AnswerUiState.Preparing },
            onThinking = { _answerState.value = AnswerUiState.Thinking },
        )) {
            is AssistantReply.Answered -> AnswerUiState.Answered(text = reply.text, sources = reply.sources)
            is AssistantReply.Failed -> AnswerUiState.Failed(reply.message)
            AssistantReply.LimitReached -> AnswerUiState.Failed(DocumentAssistant.LIMIT_REACHED_MESSAGE)
        }
    }

    companion object {
        /** Same debounce as `SearchViewModel` — see its own note on the number. */
        private const val DEBOUNCE_MILLIS = 220L

        /** Chunks considered before collapsing to one row per document. */
        private const val HIT_LIMIT = 40
        private const val RESULT_LIMIT = 12

        /** Kept as pass-throughs so existing call sites and tests need not know these moved to [DocumentAssistant]. */
        internal const val NOTHING_RELATED_MESSAGE = DocumentAssistant.NOTHING_RELATED_MESSAGE
        internal const val COULD_NOT_ASK_MESSAGE = DocumentAssistant.COULD_NOT_ASK_MESSAGE

        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AskViewModel(
                    assistant = container.documentAssistant,
                    embedderProvider = { container.embedder },
                    search = container.documentSearch::search,
                    resolveDocument = container.documentRepository::byId,
                ) as T
        }
    }
}

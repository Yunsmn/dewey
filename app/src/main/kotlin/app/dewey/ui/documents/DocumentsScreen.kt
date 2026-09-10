package app.dewey.ui.documents

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.di.AppContainer
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.search.SearchUiState
import app.dewey.ui.billing.LibrarianPaywall
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.components.rememberNotificationPermissionRequest
import app.dewey.ui.library.LibrarySection
import app.dewey.ui.library.LibraryUiState
import app.dewey.ui.library.LibraryViewModel
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.work.TaskState

/**
 * The Documents tab: the linked folder, an ask bar with search and an
 * assistant built in, and the library beneath it — free tools plus the
 * scanner live on Home; everything here needs Librarian.
 *
 * Sorting, undo and "needs a look" are [LibraryViewModel]'s own state, reused
 * rather than duplicated — this screen only adds the folder header, the ask
 * bar and the category chips around it. See [DocumentsContent] for the
 * layout itself, kept apart from this so a preview never needs a real
 * [AppContainer].
 */
@Composable
fun DocumentsScreen(
    container: AppContainer,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
    initialCategory: String? = null,
) {
    val entitled by container.entitlements.isEntitled.collectAsStateWithLifecycle(
        initialValue = !container.entitlements.isConfigured,
    )

    if (!entitled) {
        DocumentsLockedShowcase(entitlements = container.entitlements, modifier = modifier)
        return
    }

    val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container))
    val askViewModel: AskViewModel = viewModel(factory = AskViewModel.factory(container))

    val libraryState by libraryViewModel.state.collectAsStateWithLifecycle()
    val askQuery by askViewModel.query.collectAsStateWithLifecycle()
    val searchState by askViewModel.searchState.collectAsStateWithLifecycle()
    val answerState by askViewModel.answerState.collectAsStateWithLifecycle()
    val grantedTrees by container.documentTreeStore.grantedTrees.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }

    // Same moment LibraryScreen asks — granting a folder is the first time a
    // progress notification has anything to say.
    val askAboutNotifications = rememberNotificationPermissionRequest()
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            libraryViewModel.onFolderGranted(it)
            askAboutNotifications()
        }
    }

    if (libraryState.showPaywall) {
        LibrarianPaywall(
            entitlements = container.entitlements,
            onDismiss = libraryViewModel::onPaywallDismissed,
        )
    }

    val folderName = grantedTrees.firstOrNull()?.let { uri ->
        folderDisplayName(DocumentsContract.getTreeDocumentId(uri))
    }

    DocumentsContent(
        libraryState = libraryState,
        folderName = folderName,
        askQuery = askQuery,
        searchState = searchState,
        answerState = answerState,
        selectedCategory = selectedCategory,
        onSelectCategory = { selectedCategory = it },
        onAddFolder = { pickFolder.launch(null) },
        onCancelIndexing = libraryViewModel::onCancelIndexing,
        onSort = libraryViewModel::onSort,
        onUndo = libraryViewModel::onUndo,
        onQueryChanged = askViewModel::onQueryChanged,
        onAsk = askViewModel::onAsk,
        onOpenDocument = onOpenDocument,
        modifier = modifier,
    )
}

/**
 * The stateless layout every preview below renders directly, with no
 * [AppContainer] and no ViewModel in sight.
 */
@Composable
private fun DocumentsContent(
    libraryState: LibraryUiState,
    folderName: String?,
    askQuery: String,
    searchState: SearchUiState,
    answerState: AnswerUiState,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onAddFolder: () -> Unit,
    onCancelIndexing: () -> Unit,
    onSort: () -> Unit,
    onUndo: () -> Unit,
    onQueryChanged: (String) -> Unit,
    onAsk: () -> Unit,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    // A non-blank ask bar takes over the space the library normally holds —
    // the same "search replaces the list" shape app.dewey.ui.search.SearchScreen
    // uses, so typing here never sits on top of a second, disagreeing list.
    val isAsking = askQuery.isNotBlank()

    Scaffold(modifier = modifier, containerColor = Dewey.colors.paper) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = Dewey.spacing.gutter,
                end = Dewey.spacing.gutter,
                top = Dewey.spacing.block,
                bottom = NavBarClearance,
            ),
        ) {
            item(key = "header") {
                DocumentsFolderHeader(
                    folderName = folderName,
                    documentCount = libraryState.totalDocuments,
                    onAddFolder = onAddFolder,
                )
                Spacer(Modifier.height(Dewey.spacing.gutter))
            }

            item(key = "ask-bar") {
                AskBar(query = askQuery, onQueryChanged = onQueryChanged, onAsk = onAsk)
            }

            if (isAsking) {
                item(key = "ask-answer") {
                    Spacer(Modifier.height(Dewey.spacing.row))
                    AnswerCard(state = answerState, onOpenDocument = onOpenDocument)
                }
                askResultsSection(searchState = searchState, onOpenDocument = onOpenDocument)
            } else {
                item(key = "documents-task-spacer") { Spacer(Modifier.height(Dewey.spacing.gutter)) }

                documentsTaskSection(
                    state = libraryState,
                    onCancelIndexing = onCancelIndexing,
                    onSort = onSort,
                    onUndo = onUndo,
                    onOpenDocument = onOpenDocument,
                )

                if (libraryState.sections.isNotEmpty()) {
                    item(key = "categories") {
                        Spacer(Modifier.height(Dewey.spacing.block))
                        DocumentsCategoryChips(
                            sections = libraryState.sections,
                            selected = selectedCategory,
                            onSelect = onSelectCategory,
                        )
                        Spacer(Modifier.height(Dewey.spacing.row))
                    }
                }

                if (!libraryState.isEmpty) {
                    documentRows(
                        documents = documentsForCategory(libraryState.sections, selectedCategory),
                        onOpenDocument = onOpenDocument,
                    )
                }
            }
        }
    }
}

/** The ask bar's own results — search-as-you-type, in place of the library while [SearchUiState] is anything but idle. */
private fun LazyListScope.askResultsSection(
    searchState: SearchUiState,
    onOpenDocument: (Document) -> Unit,
) {
    when (searchState) {
        SearchUiState.Idle -> Unit
        SearchUiState.Preparing -> item(key = "ask-status") { AskStatusText("Waking the reader…") }
        SearchUiState.Searching -> item(key = "ask-status") { AskStatusText("Looking…") }
        is SearchUiState.Failed -> item(key = "ask-status") { AskStatusText(searchState.message, isError = true) }
        is SearchUiState.Results ->
            if (searchState.isEmpty) {
                item(key = "ask-status") { AskStatusText("Nothing matched “${searchState.query}”.") }
            } else {
                items(searchState.results, key = { "ask-result-${it.document.id}" }) { result ->
                    AskSearchResultRow(result = result, onOpenDocument = onOpenDocument)
                    HorizontalDivider(color = Dewey.colors.rule, thickness = 1.dp)
                }
            }
    }
}

@Composable
private fun AskStatusText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = Dewey.type.Body,
        color = if (isError) Dewey.colors.danger else Dewey.colors.inkMuted,
        modifier = Modifier.padding(vertical = Dewey.spacing.row),
    )
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun DocumentsLightPreview() {
    DeweyTheme(dark = false) {
        DocumentsContent(
            libraryState = previewLibraryState,
            folderName = "Downloads",
            askQuery = "",
            searchState = SearchUiState.Idle,
            answerState = AnswerUiState.Idle,
            selectedCategory = null,
            onSelectCategory = {},
            onAddFolder = {},
            onCancelIndexing = {},
            onSort = {},
            onUndo = {},
            onQueryChanged = {},
            onAsk = {},
            onOpenDocument = {},
        )
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun DocumentsDarkPreview() {
    DeweyTheme(dark = true) {
        DocumentsContent(
            libraryState = previewLibraryState,
            folderName = "Downloads",
            askQuery = "",
            searchState = SearchUiState.Idle,
            answerState = AnswerUiState.Idle,
            selectedCategory = null,
            onSelectCategory = {},
            onAddFolder = {},
            onCancelIndexing = {},
            onSort = {},
            onUndo = {},
            onQueryChanged = {},
            onAsk = {},
            onOpenDocument = {},
        )
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun DocumentsAskingPreview() {
    DeweyTheme(dark = false) {
        DocumentsContent(
            libraryState = previewLibraryState,
            folderName = "Downloads",
            askQuery = "when is my insurance due",
            searchState = SearchUiState.Idle,
            answerState = AnswerUiState.Answered(
                text = "Your car insurance with Wafa Assurance renews on 12 March 2026.",
                sources = listOf(previewDocuments[0]),
            ),
            selectedCategory = null,
            onSelectCategory = {},
            onAddFolder = {},
            onCancelIndexing = {},
            onSort = {},
            onUndo = {},
            onQueryChanged = {},
            onAsk = {},
            onOpenDocument = {},
        )
    }
}

@Preview(heightDp = 900, widthDp = 390)
@Composable
private fun DocumentsEmptyPreview() {
    DeweyTheme(dark = false) {
        DocumentsContent(
            libraryState = LibraryUiState(),
            folderName = null,
            askQuery = "",
            searchState = SearchUiState.Idle,
            answerState = AnswerUiState.Idle,
            selectedCategory = null,
            onSelectCategory = {},
            onAddFolder = {},
            onCancelIndexing = {},
            onSort = {},
            onUndo = {},
            onQueryChanged = {},
            onAsk = {},
            onOpenDocument = {},
        )
    }
}

private val previewDocuments = listOf(
    Document(1, "u1", "Wafa Assurance.pdf", 1080, 0, 1, DocType.INSURANCE, vendor = "Wafa Assurance"),
    Document(2, "u2", "document (5).pdf", 1080, 0, 1, DocType.UTILITY_BILL),
    Document(3, "u3", "WhatsApp Doc 2022-01-20 at 10.22.24.pdf", 2400, 0, 2, DocType.BANK_STATEMENT),
)

private val previewLibraryState = LibraryUiState(
    sections = listOf(
        LibrarySection("Insurance", previewDocuments.subList(0, 1)),
        LibrarySection("Bills", previewDocuments.subList(1, 2)),
        LibrarySection("Bank", previewDocuments.subList(2, 3)),
    ),
    totalDocuments = 3,
    grantedFolders = 1,
    task = TaskState.Idle,
)

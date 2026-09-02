package app.dewey.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.search.SearchResult
import app.dewey.search.SearchUiState
import app.dewey.ui.components.SectionHeading
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

    SearchContent(
        query = query,
        state = state,
        onQueryChanged = viewModel::onQueryChanged,
        onSubmit = viewModel::onSubmit,
        onOpenDocument = onOpenDocument,
        modifier = modifier,
    )
}

@Composable
private fun SearchContent(
    query: String,
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenDocument: (Document) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Dewey.colors.paper)
            .padding(horizontal = Dewey.spacing.gutter),
    ) {
        Spacer(Modifier.height(Dewey.spacing.block))
        Text("Find", style = Dewey.type.Display, color = Dewey.colors.ink)
        Spacer(Modifier.height(Dewey.spacing.row))

        QueryField(
            query = query,
            onQueryChanged = onQueryChanged,
            onSubmit = onSubmit,
            modifier = Modifier.focusRequester(focus),
        )

        Spacer(Modifier.height(Dewey.spacing.gutter))

        when (state) {
            SearchUiState.Idle -> Hint()
            SearchUiState.Preparing -> Status("Waking the reader…")
            SearchUiState.Searching -> Status("Looking…")
            is SearchUiState.Failed -> Status(state.message, isError = true)
            is SearchUiState.Results ->
                if (state.isEmpty) {
                    Status("Nothing matched “${state.query}”.")
                } else {
                    ResultList(state.results, state.query, onOpenDocument)
                }
        }
    }
}

@Composable
private fun QueryField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = LocalTextStyle.current.merge(
                Dewey.type.Title.copy(color = Dewey.colors.ink)
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { field ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = "what was it about?",
                            style = Dewey.type.Title,
                            color = Dewey.colors.inkFaint,
                        )
                    }
                    field()
                }
            },
        )
        Spacer(Modifier.height(Dewey.spacing.tight))
        // The field is a written line, not a boxed input. Consistent with a
        // surface that is meant to read as paper.
        HorizontalDivider(color = Dewey.colors.ink, thickness = 1.dp)
    }
}

@Composable
private fun ResultList(
    results: List<SearchResult>,
    query: String,
    onOpenDocument: (Document) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = Dewey.spacing.section)) {
        item(key = "heading") {
            SectionHeading(label = "Results", count = results.size)
        }
        items(results, key = { it.document.id }) { result ->
            ResultRow(result, query) { onOpenDocument(result.document) }
            HorizontalDivider(color = Dewey.colors.rule, thickness = 1.dp)
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, query: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dewey.spacing.row),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = result.document.displayName,
                style = Dewey.type.Mono,
                color = Dewey.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Dewey.spacing.tight))
        // The passage that matched, with the query's own words picked out. This
        // is the evidence that the right file came back — the filename alone is
        // exactly what the user could not recognise in the first place.
        Text(
            text = highlight(result.snippet.take(SNIPPET_CHARS), query),
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Bolds any query word that appears in the passage. */
private fun highlight(snippet: String, query: String) = buildAnnotatedString {
    val terms = query.split(' ', ',', '.', ':').filter { it.length >= MIN_HIGHLIGHT }.map { it.lowercase() }
    val lower = snippet.lowercase()

    var cursor = 0
    while (cursor < snippet.length) {
        val next = terms
            .mapNotNull { term -> lower.indexOf(term, cursor).takeIf { it >= 0 }?.let { it to term.length } }
            .minByOrNull { it.first }

        if (next == null) {
            append(snippet.substring(cursor))
            break
        }
        val (start, length) = next
        append(snippet.substring(cursor, start))
        withStyle(SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)) {
            append(snippet.substring(start, start + length))
        }
        cursor = start + length
    }
}

@Composable
private fun Hint() {
    Text(
        text = "Describe the document. “the electricity bill from the month the " +
            "meter was changed” works better than a filename.",
        style = Dewey.type.Body,
        color = Dewey.colors.inkMuted,
    )
}

@Composable
private fun Status(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = Dewey.type.Body,
        color = if (isError) Dewey.colors.danger else Dewey.colors.inkMuted,
    )
}

private const val SNIPPET_CHARS = 260
private const val MIN_HIGHLIGHT = 3

@Preview(showBackground = true, backgroundColor = 0xFFFAF7F2, heightDp = 700, widthDp = 390)
@Composable
private fun SearchPreview() {
    val document = Document(1, "u", "document (5).pdf", 1080, 0, 1, DocType.UTILITY_BILL)
    DeweyTheme {
        SearchContent(
            query = "electricity bill january",
            state = SearchUiState.Results(
                query = "electricity bill january",
                results = listOf(
                    SearchResult(
                        document = document,
                        snippet = "LYDEC — Facture d'electricite et d'eau. Periode : janvier 2023. " +
                            "Client : Youssef Tazi. Montant total a payer : 281.26 MAD.",
                        score = 0.031,
                    ),
                ),
            ),
            onQueryChanged = {},
            onSubmit = {},
            onOpenDocument = {},
        )
    }
}

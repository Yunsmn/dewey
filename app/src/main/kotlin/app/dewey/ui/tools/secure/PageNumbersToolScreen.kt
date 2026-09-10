package app.dewey.ui.tools.secure

import app.dewey.ui.tools.ToolTextField
import app.dewey.ui.tools.OptionRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.Corner
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.formatPageNumber
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs

/** The number a "show total" chip previews with, standing in until a real document is chosen. */
private const val PREVIEW_NUMBER = 3
private const val PREVIEW_TOTAL_FALLBACK = 12

@Composable
fun PageNumbersToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: PageNumbersViewModel = viewModel(factory = PageNumbersViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::onDestinationChosen)

    PageNumbersContent(
        state = state,
        onPickSource = pickSource,
        onCornerChosen = viewModel::onCornerChosen,
        onStartingNumberChanged = viewModel::onStartingNumberChanged,
        onShowTotalChosen = viewModel::onShowTotalChosen,
        onRun = { saveAs(state.suggestedFileName) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun PageNumbersContent(
    state: PageNumbersUiState,
    onPickSource: () -> Unit,
    onCornerChosen: (Corner) -> Unit,
    onStartingNumberChanged: (String) -> Unit,
    onShowTotalChosen: (Boolean) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Page numbers",
        description = "Stamp a running number onto every page, in whichever corner suits the document.",
        state = state.run,
        runLabel = "Number and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Position")
        CornerGrid(selected = state.corner, onSelect = onCornerChosen)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Starting number")
        ToolTextField(
            value = state.startingNumberText,
            onValueChange = onStartingNumberChanged,
            placeholder = "1",
            keyboardType = KeyboardType.Number,
        )

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Total")
        OptionRow(
            options = listOf(false, true),
            selected = state.showTotal,
            label = { showTotal -> formatPageNumber(PREVIEW_NUMBER, state.pageCount ?: PREVIEW_TOTAL_FALLBACK, showTotal) },
            onSelected = onShowTotalChosen,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun PageNumbersPreview() {
    DeweyTheme {
        PageNumbersContent(
            state = PageNumbersUiState(
                source = PickedFile(android.net.Uri.EMPTY, "report.pdf", 240_000),
                pageCount = 24,
                corner = Corner.BOTTOM_RIGHT,
            ),
            onPickSource = {},
            onCornerChosen = {},
            onStartingNumberChanged = {},
            onShowTotalChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}

package app.dewey.ui.tools.raster

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.RasterQuality
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun CompressToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: CompressViewModel = viewModel(factory = CompressViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::onDestinationChosen)

    CompressContent(
        state = state,
        onPickSource = pickSource,
        onQualityChosen = viewModel::onQualityChosen,
        // As with the other raster tools, running means opening the save
        // picker first; the compress itself starts from onDestinationChosen.
        onRun = { saveAs(state.suggestedFileName) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun CompressContent(
    state: CompressUiState,
    onPickSource: () -> Unit,
    onQualityChosen: (RasterQuality) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Compress",
        description = "Shrink a scanned PDF by redrawing its pages at a lower resolution. Works best on " +
            "documents that are mostly photographed pages rather than typed text.",
        state = state.run,
        runLabel = "Compress and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Quality")
        OptionRow(
            options = RasterQuality.entries,
            selected = state.quality,
            label = RasterQuality::label,
            onSelected = onQualityChosen,
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun CompressPreview() {
    DeweyTheme {
        CompressContent(
            state = CompressUiState(source = PickedFile(android.net.Uri.EMPTY, "scanned-book.pdf", 18_400_000)),
            onPickSource = {},
            onQualityChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 700, widthDp = 390)
@Composable
private fun CompressLargerPreview() {
    DeweyTheme {
        CompressContent(
            state = CompressUiState(
                source = PickedFile(android.net.Uri.EMPTY, "contract.pdf", 240_000),
                run = ToolRunState.Done("The result came out larger than the original — this PDF is probably already compact."),
            ),
            onPickSource = {},
            onQualityChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}

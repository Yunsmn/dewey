package app.dewey.ui.tools.raster

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.RasterImageFormat
import app.dewey.pdf.RasterQuality
import app.dewey.ui.components.GlassCard
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.rememberFolderPicker
import app.dewey.ui.tools.rememberPdfPicker

@Composable
fun PdfToImagesToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: PdfToImagesViewModel = viewModel(factory = PdfToImagesViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val pickDestination = rememberFolderPicker(onPicked = viewModel::onDestinationPicked)

    PdfToImagesContent(
        state = state,
        onPickSource = pickSource,
        onPickDestination = pickDestination,
        onFormatChosen = viewModel::onFormatChosen,
        onQualityChosen = viewModel::onQualityChosen,
        onRun = viewModel::run,
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun PdfToImagesContent(
    state: PdfToImagesUiState,
    onPickSource: () -> Unit,
    onPickDestination: () -> Unit,
    onFormatChosen: (RasterImageFormat) -> Unit,
    onQualityChosen: (RasterQuality) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "PDF to images",
        description = "Save each page of a document as its own picture, in a folder you choose.",
        state = state.run,
        runLabel = "Convert pages",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Format")
        OptionRow(
            options = RasterImageFormat.entries,
            selected = state.format,
            label = RasterImageFormat::label,
            onSelected = onFormatChosen,
        )

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Quality")
        OptionRow(
            options = RasterQuality.entries,
            selected = state.quality,
            label = RasterQuality::label,
            onSelected = onQualityChosen,
        )

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Save to")
        FolderSlot(folderName = state.destinationName, prompt = "Choose a folder", onPick = onPickDestination)
    }
}

/**
 * [FileSlot]'s sibling for a folder rather than a single document: a folder
 * chosen through [app.dewey.ui.tools.rememberFolderPicker] has a name but no
 * meaningful single size to show beside it, so this drops that second line
 * rather than showing "size unknown" for something that was never a file.
 */
@Composable
private fun FolderSlot(folderName: String?, prompt: String, onPick: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth(), onClick = onPick) {
        if (folderName == null) {
            Text(prompt, style = Dewey.type.Body, color = Dewey.colors.accent)
        } else {
            Text(
                text = folderName,
                style = Dewey.type.Mono,
                color = Dewey.colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Dewey.spacing.hairline))
            Text("tap to change", style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun PdfToImagesPreview() {
    DeweyTheme {
        PdfToImagesContent(
            state = PdfToImagesUiState(
                source = PickedFile(android.net.Uri.EMPTY, "lease.pdf", 482_000),
                destination = android.net.Uri.EMPTY,
                destinationName = "Scans",
            ),
            onPickSource = {},
            onPickDestination = {},
            onFormatChosen = {},
            onQualityChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun PdfToImagesDonePreview() {
    DeweyTheme {
        PdfToImagesContent(
            state = PdfToImagesUiState(
                source = PickedFile(android.net.Uri.EMPTY, "statement.pdf", 900_000),
                destination = android.net.Uri.EMPTY,
                destinationName = "Statements",
                run = ToolRunState.Done("Saved 11 of 12 pages. Page 7 couldn't be saved."),
            ),
            onPickSource = {},
            onPickDestination = {},
            onFormatChosen = {},
            onQualityChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}

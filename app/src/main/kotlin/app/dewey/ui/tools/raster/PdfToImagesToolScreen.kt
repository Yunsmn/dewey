package app.dewey.ui.tools.raster

import app.dewey.ui.tools.OptionRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.RasterImageFormat
import app.dewey.pdf.RasterQuality
import app.dewey.ui.components.IconTile
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.hue
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
        hue = ToolDestination.PDF_TO_IMAGES.group.hue,
        icon = ToolDestination.PDF_TO_IMAGES.icon,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource, hue = ToolDestination.PDF_TO_IMAGES.group.hue)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Format")
        OptionRow(
            options = RasterImageFormat.entries,
            selected = state.format,
            label = RasterImageFormat::label,
            onSelected = onFormatChosen,
            hue = ToolDestination.PDF_TO_IMAGES.group.hue,
        )

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Quality")
        OptionRow(
            options = RasterQuality.entries,
            selected = state.quality,
            label = RasterQuality::label,
            onSelected = onQualityChosen,
            hue = ToolDestination.PDF_TO_IMAGES.group.hue,
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
 * Same soft sunken slot as [FileSlot], just with a folder glyph.
 */
@Composable
private fun FolderSlot(folderName: String?, prompt: String, onPick: () -> Unit) {
    val hue = ToolDestination.PDF_TO_IMAGES.group.hue
    val shape = RoundedCornerShape(Dewey.radii.medium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Dewey.colors.paperSunken)
            .clickable(onClick = onPick)
            .padding(Dewey.spacing.gutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        IconTile(icon = Icons.Rounded.Folder, hue = hue, size = 40.dp)
        if (folderName == null) {
            Text(prompt, style = Dewey.type.Body, color = Dewey.colors.ink)
        } else {
            Column {
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

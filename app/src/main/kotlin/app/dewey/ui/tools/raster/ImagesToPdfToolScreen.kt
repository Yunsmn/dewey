package app.dewey.ui.tools.raster

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.RasterQuality
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.formatBytes
import app.dewey.ui.tools.rememberImagesPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun ImagesToPdfToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val viewModel: ImagesToPdfViewModel = viewModel(factory = ImagesToPdfViewModel.factory(toolkit))
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickImages = rememberImagesPicker(onPicked = viewModel::onImagesPicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::onDestinationChosen)

    ImagesToPdfContent(
        state = state,
        onPickImages = pickImages,
        onQualityChosen = viewModel::onQualityChosen,
        onMove = viewModel::onMove,
        onRemove = viewModel::onRemove,
        // The run button opens the save picker directly; the actual assembly
        // only starts once onDestinationChosen fires with a real target.
        onRun = { saveAs(state.suggestedFileName) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun ImagesToPdfContent(
    state: ImagesToPdfUiState,
    onPickImages: () -> Unit,
    onQualityChosen: (RasterQuality) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ToolScaffold(
        title = "Images to PDF",
        description = "Combine photos or scans into one PDF, in the order you arrange them.",
        state = state.run,
        runLabel = "Build PDF",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
    ) {
        FieldLabel("Images")
        if (state.images.isEmpty()) {
            FileSlot(file = null, prompt = "Choose images", onPick = onPickImages)
        } else {
            val lastIndex = state.images.lastIndex
            Column(verticalArrangement = Arrangement.spacedBy(Dewey.spacing.tight)) {
                state.images.forEachIndexed { index, file ->
                    ImageListItem(
                        file = file,
                        index = index,
                        lastIndex = lastIndex,
                        onMoveUp = { onMove(index, index - 1) },
                        onMoveDown = { onMove(index, index + 1) },
                        onRemove = { onRemove(index) },
                    )
                }
            }
            Spacer(Modifier.height(Dewey.spacing.tight))
            SecondaryAction(label = "Add more images", onClick = onPickImages, modifier = Modifier.fillMaxWidth())
        }

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

/**
 * One image in the assembly order: its name and size, and the only three
 * things a person needs to do to it here — move it earlier, move it later,
 * or drop it. A drag handle would need its own gesture-conflict handling
 * inside an already-scrolling screen; two arrows do the same job without it.
 */
@Composable
private fun ImageListItem(
    file: PickedFile,
    index: Int,
    lastIndex: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${index + 1}. ${file.name.ifEmpty { "Image" }}",
                    style = Dewey.type.Mono,
                    color = Dewey.colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Dewey.spacing.hairline))
                Text(formatBytes(file.sizeBytes), style = Dewey.type.Meta, color = Dewey.colors.inkMuted)
            }
            IconButton(onClick = onMoveUp, enabled = index > 0) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowUp,
                    contentDescription = "Move earlier",
                    tint = if (index > 0) Dewey.colors.ink else Dewey.colors.inkFaint,
                )
            }
            IconButton(onClick = onMoveDown, enabled = index < lastIndex) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Move later",
                    tint = if (index < lastIndex) Dewey.colors.ink else Dewey.colors.inkFaint,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(imageVector = Icons.Outlined.Close, contentDescription = "Remove", tint = Dewey.colors.danger)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ImagesToPdfPreview() {
    DeweyTheme {
        ImagesToPdfContent(
            state = ImagesToPdfUiState(
                images = listOf(
                    PickedFile(android.net.Uri.EMPTY, "IMG_001.jpg", 2_400_000),
                    PickedFile(android.net.Uri.EMPTY, "IMG_002.jpg", 2_100_000),
                    PickedFile(android.net.Uri.EMPTY, "IMG_003.jpg", 2_600_000),
                ),
            ),
            onPickImages = {},
            onQualityChosen = {},
            onMove = { _, _ -> },
            onRemove = {},
            onRun = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ImagesToPdfDonePreview() {
    DeweyTheme {
        ImagesToPdfContent(
            state = ImagesToPdfUiState(
                images = listOf(
                    PickedFile(android.net.Uri.EMPTY, "IMG_001.jpg", 2_400_000),
                    PickedFile(android.net.Uri.EMPTY, "IMG_002.jpg", 2_100_000),
                ),
                run = ToolRunState.Done("Placed 1 of 2 images into the PDF. 1 image couldn't be read."),
            ),
            onPickImages = {},
            onQualityChosen = {},
            onMove = { _, _ -> },
            onRemove = {},
            onRun = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ImagesToPdfEmptyPreview() {
    DeweyTheme {
        ImagesToPdfContent(
            state = ImagesToPdfUiState(),
            onPickImages = {},
            onQualityChosen = {},
            onMove = { _, _ -> },
            onRemove = {},
            onRun = {},
            onReset = {},
        )
    }
}

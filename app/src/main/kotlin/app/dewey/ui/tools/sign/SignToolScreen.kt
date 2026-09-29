package app.dewey.ui.tools.sign

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dewey.pdf.Corner
import app.dewey.pdf.PdfToolkit
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.theme.Hue
import app.dewey.ui.tools.FieldLabel
import app.dewey.ui.tools.FileSlot
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolDestination
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.ToolScaffold
import app.dewey.ui.tools.hue
import app.dewey.ui.tools.rememberPdfPicker
import app.dewey.ui.tools.rememberSaveAs

@Composable
fun SignToolScreen(toolkit: PdfToolkit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // The placement preview is rendered at the screen's own width so a drag
    // on it tracks the finger at roughly one screen pixel per gesture pixel.
    val screenWidthPx = remember(configuration.screenWidthDp, density) {
        with(density) { configuration.screenWidthDp.dp.roundToPx() }
    }

    val viewModel: SignToolViewModel = viewModel(
        factory = SignToolViewModel.factory(toolkit, context.filesDir, screenWidthPx),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    val pickSource = rememberPdfPicker(onPicked = viewModel::onSourcePicked)
    val saveAs = rememberSaveAs(onCreated = viewModel::onDestinationChosen)

    SignContent(
        state = state,
        onPickSource = pickSource,
        onSignatureDrawn = viewModel::onSignatureDrawn,
        onUseSavedSignature = viewModel::onUseSavedSignature,
        onClearSignature = viewModel::onClearSignature,
        onPageNumberStep = viewModel::onStepPage,
        onDrag = viewModel::onDrag,
        onCornerChosen = viewModel::onCornerChosen,
        onSizeChosen = viewModel::onSizeChosen,
        onRun = { saveAs(state.suggestedFileName) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun SignContent(
    state: SignUiState,
    onPickSource: () -> Unit,
    onSignatureDrawn: (Bitmap) -> Unit,
    onUseSavedSignature: () -> Unit,
    onClearSignature: () -> Unit,
    onPageNumberStep: (Int) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onCornerChosen: (Corner) -> Unit,
    onSizeChosen: (Float) -> Unit,
    onRun: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hue = ToolDestination.SIGN.group.hue

    ToolScaffold(
        title = "Sign",
        description = "Draw your signature once, then drag it onto the page it belongs on.",
        state = state.run,
        runLabel = "Sign and save",
        canRun = state.canRun,
        onRun = onRun,
        onReset = onReset,
        modifier = modifier,
        hue = hue,
        icon = ToolDestination.SIGN.icon,
    ) {
        FieldLabel("Document")
        FileSlot(file = state.source, prompt = "Choose a PDF", onPick = onPickSource, hue = hue)

        Spacer(Modifier.height(Dewey.spacing.row))
        FieldLabel("Signature")
        SignatureSection(
            signature = state.signature,
            hasSavedSignature = state.hasSavedSignature,
            onSignatureDrawn = onSignatureDrawn,
            onUseSavedSignature = onUseSavedSignature,
            onClearSignature = onClearSignature,
            hue = hue,
        )

        val preview = state.preview
        val signature = state.signature
        val pageCount = state.pageCount
        if (preview != null && signature != null && pageCount != null) {
            Spacer(Modifier.height(Dewey.spacing.row))
            FieldLabel("Page")
            PageStepper(
                pageNumber = state.pageNumber ?: pageCount,
                pageCount = pageCount,
                onStep = onPageNumberStep,
                hue = hue,
            )

            Spacer(Modifier.height(Dewey.spacing.row))
            FieldLabel("Position")
            PlacementPreview(
                preview = preview,
                signature = signature,
                placement = state.placement,
                lastCorner = state.lastCorner,
                onDrag = onDrag,
                onCornerChosen = onCornerChosen,
                onSizeChosen = onSizeChosen,
                hue = hue,
            )
        }
    }
}

/**
 * Draws a new signature, or shows the one already chosen with a way to start
 * over. A signature can also be reused from a previous session — see
 * [saveSignature] — via [onUseSavedSignature], offered only when one exists.
 */
@Composable
private fun SignatureSection(
    signature: Bitmap?,
    hasSavedSignature: Boolean,
    onSignatureDrawn: (Bitmap) -> Unit,
    onUseSavedSignature: () -> Unit,
    onClearSignature: () -> Unit,
    hue: Hue,
) {
    if (signature != null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(Dewey.radii.small))
                    .background(Dewey.colors.paperSunken),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = signature.asImageBitmap(),
                    contentDescription = "Your signature",
                    modifier = Modifier.size(52.dp),
                )
            }
            SecondaryAction(label = "Redraw", onClick = onClearSignature)
        }
        return
    }

    val padState = remember { SignaturePadState() }
    val density = LocalDensity.current
    val ink = Dewey.colors.ink

    Column {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val padWidthPx = with(density) { maxWidth.toPx() }
            val padHeightPx = with(density) { SIGNATURE_PAD_HEIGHT.toPx() }

            SignaturePad(state = padState, modifier = Modifier.fillMaxWidth().height(SIGNATURE_PAD_HEIGHT))

            Spacer(Modifier.height(Dewey.spacing.tight))
            Row(horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row)) {
                SecondaryAction(label = "Clear", onClick = padState::clear)
                SecondaryAction(
                    label = "Use this signature",
                    onClick = {
                        if (padState.hasInk) {
                            val strokeWidthPx = minOf(padWidthPx, padHeightPx) * SIGNATURE_STROKE_WIDTH_FRACTION
                            onSignatureDrawn(padState.toBitmap(padWidthPx.toInt(), padHeightPx.toInt(), ink, strokeWidthPx))
                        }
                    },
                )
            }
        }

        if (hasSavedSignature) {
            Spacer(Modifier.height(Dewey.spacing.tight))
            SecondaryAction(label = "Use saved signature", onClick = onUseSavedSignature)
        }
    }
}

private val SIGNATURE_PAD_HEIGHT = 160.dp

/** A minus/number/plus row rather than a bare text field — a page number is picked far more often than typed. */
@Composable
private fun PageStepper(pageNumber: Int, pageCount: Int, onStep: (Int) -> Unit, hue: Hue) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Dewey.radii.small))
            .background(Dewey.colors.paperSunken)
            .padding(horizontal = Dewey.spacing.tight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dewey.spacing.row),
    ) {
        IconButton(onClick = { onStep(-1) }, enabled = pageNumber > 1) {
            Icon(Icons.Rounded.Remove, contentDescription = "Previous page", tint = hue.strong)
        }
        Text("Page $pageNumber of $pageCount", style = Dewey.type.Body, color = Dewey.colors.ink)
        IconButton(onClick = { onStep(1) }, enabled = pageNumber < pageCount) {
            Icon(Icons.Rounded.Add, contentDescription = "Next page", tint = hue.strong)
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 900, widthDp = 390)
@Composable
private fun SignToolScreenDrawingDarkPreview() {
    DeweyTheme(dark = true) {
        SignContent(
            state = SignUiState(
                source = PickedFile(Uri.EMPTY, "lease.pdf", 482_000),
                pageCount = 6,
            ),
            onPickSource = {},
            onSignatureDrawn = {},
            onUseSavedSignature = {},
            onClearSignature = {},
            onPageNumberStep = {},
            onDrag = { _, _ -> },
            onCornerChosen = {},
            onSizeChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F6FA, heightDp = 900, widthDp = 390)
@Composable
private fun SignToolScreenPlacementLightPreview() {
    DeweyTheme(dark = false) {
        val previewBitmap = remember { Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888) }
        val signatureBitmap = remember { Bitmap.createBitmap(220, 90, Bitmap.Config.ARGB_8888) }
        SignContent(
            state = SignUiState(
                source = PickedFile(Uri.EMPTY, "contract.pdf", 900_000),
                pageCount = 6,
                pageNumberText = "6",
                preview = previewBitmap,
                signature = signatureBitmap,
                run = ToolRunState.Done("Added your signature to page 6."),
            ),
            onPickSource = {},
            onSignatureDrawn = {},
            onUseSavedSignature = {},
            onClearSignature = {},
            onPageNumberStep = {},
            onDrag = { _, _ -> },
            onCornerChosen = {},
            onSizeChosen = {},
            onRun = {},
            onReset = {},
        )
    }
}

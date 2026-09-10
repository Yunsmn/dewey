package app.dewey.ui.scan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HighlightOff
import app.dewey.ui.components.GlassCard
import app.dewey.ui.components.NavBarClearance
import app.dewey.ui.components.PrimaryAction
import app.dewey.ui.components.SecondaryAction
import app.dewey.ui.theme.Dewey
import app.dewey.ui.theme.DeweyTheme
import app.dewey.ui.tools.PDF_MIME
import app.dewey.ui.tools.rememberSaveAs
import java.time.LocalDateTime
import kotlinx.coroutines.delay

/**
 * The scan screen: a large capture affordance, and the honest outcome of
 * pressing it.
 *
 * This is deliberately not a viewfinder. ML Kit's [app.dewey.scan.DocumentScanner]
 * runs the whole capture-and-crop experience in its own activity, so there is
 * no camera preview or edge-detection overlay for this screen to draw without
 * lying about what it does — see the state machine on [ScanUiState].
 *
 * Saving is this screen's own concern: tapping Save on a finished scan opens
 * a SAF create-document flow for a suggested name (see [scanFileName]), and
 * the target it returns is handed to [ScanViewModel.save], which copies the
 * bytes off ML Kit's own storage — see [ScanUiState.Scanned] for why that
 * copy can't be skipped.
 */
@Composable
fun ScanScreen(
    viewModel: ScanViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result -> viewModel.onScanResult(result) }

    val saveAs = rememberSaveAs(PDF_MIME) { targetUri -> viewModel.save(targetUri) }

    // Launches exactly once per ReadyToLaunch value: LaunchedEffect is keyed on
    // the state instance, and onScanLaunched immediately moves state off
    // ReadyToLaunch, so a recomposition of this screen can't fire the same
    // intent sender twice.
    val ready = state
    if (ready is ScanUiState.ReadyToLaunch) {
        LaunchedEffect(ready) {
            launcher.launch(IntentSenderRequest.Builder(ready.intentSender).build())
            viewModel.onScanLaunched()
        }
    }

    ScanContent(
        state = state,
        onScan = { activity?.let(viewModel::startScan) },
        onSave = { saveAs(scanFileName(LocalDateTime.now())) },
        onReset = viewModel::reset,
        modifier = modifier,
    )
}

@Composable
private fun ScanContent(
    state: ScanUiState,
    onScan: () -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = Dewey.colors.paper,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    PaddingValues(
                        start = Dewey.spacing.gutter,
                        end = Dewey.spacing.gutter,
                        top = Dewey.spacing.block,
                        bottom = NavBarClearance,
                    ),
                ),
        ) {
            Text("Scan", style = Dewey.type.Display, color = Dewey.colors.ink)
            Spacer(Modifier.height(Dewey.spacing.section))

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = state,
                    label = "scanState",
                    transitionSpec = { fadeTransition() },
                    // Preparing, ReadyToLaunch and Scanning all render the same
                    // PreparingNotice — grouping them under one key stops a
                    // crossfade (and PreparingNotice's own delayed-hint timer
                    // restarting) between three states that look identical.
                    contentKey = { it.animationKey() },
                ) { current ->
                    when (current) {
                        is ScanUiState.Ready -> CaptureInvite(onScan = onScan)
                        is ScanUiState.Preparing -> PreparingNotice()
                        is ScanUiState.ReadyToLaunch -> PreparingNotice()
                        is ScanUiState.Scanning -> PreparingNotice()
                        is ScanUiState.Scanned -> ScannedResult(
                            onSave = onSave,
                            onScanAnother = onReset,
                        )
                        is ScanUiState.Saving -> SavingNotice()
                        is ScanUiState.Saved -> SavedNotice(onScanAnother = onReset)
                        is ScanUiState.SaveFailed -> OutcomeNotice(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Couldn't save the scan",
                            body = current.message,
                            actionLabel = "Scan again",
                            onAction = onScan,
                        )
                        is ScanUiState.Cancelled -> OutcomeNotice(
                            icon = Icons.Outlined.HighlightOff,
                            title = "Scan cancelled",
                            body = "Nothing was kept. You can start again whenever you're ready.",
                            actionLabel = "Scan a document",
                            onAction = onScan,
                        )
                        is ScanUiState.Unavailable -> OutcomeNotice(
                            icon = Icons.Outlined.CloudOff,
                            title = "Scanning isn't available here",
                            body = "This device can't currently run the document scanner. That's " +
                                "usually Google Play Services being missing or out of date — common " +
                                "on emulators, and on some phones. Nothing was lost.",
                            actionLabel = "Try again",
                            onAction = onScan,
                        )
                        is ScanUiState.Failed -> OutcomeNotice(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Something went wrong",
                            body = current.message,
                            actionLabel = "Try again",
                            onAction = onScan,
                        )
                    }
                }
            }
        }
    }
}

/** The starting state: one clear affordance, nothing else competing for attention. */
@Composable
private fun CaptureInvite(onScan: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CaptureButton(onClick = onScan)
        Spacer(Modifier.height(Dewey.spacing.block))
        Text(
            text = "Scan a document",
            style = Dewey.type.Title,
            color = Dewey.colors.ink,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "Capture pages, and Dewey squares them up and turns them into a PDF.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * The large circular capture affordance the whole screen is built around.
 *
 * Deliberately not a camera icon behind a live preview — see the file doc on
 * why this screen never draws a viewfinder. It is a button that hands off to
 * ML Kit's own activity, and it reads as one.
 */
@Composable
private fun CaptureButton(onClick: () -> Unit) {
    val interactions = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(112.dp)
            .background(Dewey.colors.accent, CircleShape)
            .clickable(interactionSource = interactions, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.CameraAlt,
            contentDescription = "Scan a document",
            tint = Dewey.colors.onAccent,
            modifier = Modifier.size(44.dp),
        )
    }
}

/**
 * Shown while the scanner is starting, while it's ready to launch, and while
 * its own activity is on screen. All three are "wait, nothing to do here" from
 * this screen's point of view, so they share one visual rather than flickering
 * through three near-identical ones.
 *
 * The longer message only appears after a short delay, because most of the
 * time this step resolves in well under a second — showing "this can take a
 * moment" immediately would read as a warning nothing here has earned yet.
 * When it is a genuine first-run module download, the delay is where that
 * honesty is worth showing.
 */
@Composable
private fun PreparingNotice() {
    var showsLongerNotice by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PREPARING_HINT_DELAY_MS)
        showsLongerNotice = true
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Dewey.colors.accent)
        Spacer(Modifier.height(Dewey.spacing.block))
        Text(
            text = "Getting the scanner ready",
            style = Dewey.type.Title,
            color = Dewey.colors.ink,
            textAlign = TextAlign.Center,
        )
        if (showsLongerNotice) {
            Spacer(Modifier.height(Dewey.spacing.tight))
            Text(
                text = "First use can take a little longer while the scanner finishes setting up.",
                style = Dewey.type.Body,
                color = Dewey.colors.inkMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * A completed scan. Says plainly that the file is temporary until saved,
 * because the URI behind it is only readable for as long as ML Kit's own
 * grant lasts — see [ScanUiState.Scanned].
 */
@Composable
private fun ScannedResult(onSave: () -> Unit, onScanAnother: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Outlined.CheckCircleOutline,
                contentDescription = null,
                tint = Dewey.colors.accent,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.height(Dewey.spacing.row))
            Text(
                text = "Scan captured",
                style = Dewey.type.Title,
                color = Dewey.colors.ink,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dewey.spacing.tight))
            Text(
                text = "This page only exists here for now — save it to keep it. " +
                    "It disappears once you leave this screen without saving.",
                style = Dewey.type.Body,
                color = Dewey.colors.inkMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Dewey.spacing.block))
            PrimaryAction(label = "Save", onClick = onSave, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Dewey.spacing.row))
            SecondaryAction(label = "Scan another page", onClick = onScanAnother)
        }
    }
}

/**
 * Shown while [ScanViewModel.save] is copying the scan off ML Kit's storage
 * and into the location the user chose. Its own visual rather than folded
 * into [PreparingNotice]'s "preparing" group: that group is about the scanner
 * activity starting up, and this is a different wait with a different cause.
 */
@Composable
private fun SavingNotice() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Dewey.colors.accent)
        Spacer(Modifier.height(Dewey.spacing.block))
        Text(
            text = "Saving your scan",
            style = Dewey.type.Title,
            color = Dewey.colors.ink,
            textAlign = TextAlign.Center,
        )
    }
}

/** The scan was copied to where the user chose. Offers another scan, same as [ScannedResult] does. */
@Composable
private fun SavedNotice(onScanAnother: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Outlined.CheckCircleOutline,
            contentDescription = null,
            tint = Dewey.colors.accent,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(Dewey.spacing.row))
        Text(text = "Scan saved", style = Dewey.type.Title, color = Dewey.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(
            text = "It's kept where you chose to save it.",
            style = Dewey.type.Body,
            color = Dewey.colors.inkMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Dewey.spacing.block))
        SecondaryAction(label = "Scan another", onClick = onScanAnother)
    }
}

/** The shared shape for cancelled, unavailable and failed outcomes. */
@Composable
private fun OutcomeNotice(
    icon: ImageVector,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Dewey.colors.inkMuted,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(Dewey.spacing.row))
        Text(text = title, style = Dewey.type.Title, color = Dewey.colors.ink, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dewey.spacing.tight))
        Text(text = body, style = Dewey.type.Body, color = Dewey.colors.inkMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Dewey.spacing.block))
        SecondaryAction(label = actionLabel, onClick = onAction)
    }
}

private fun fadeTransition() =
    androidx.compose.animation.fadeIn() togetherWith androidx.compose.animation.fadeOut()

/** Groups the three "nothing to show yet" states so [AnimatedContent] treats them as one step. */
private fun ScanUiState.animationKey(): Any = when (this) {
    is ScanUiState.Preparing, is ScanUiState.ReadyToLaunch, is ScanUiState.Scanning -> "preparing"
    else -> this::class
}

/** Unwraps a [Context] to the [Activity] hosting it, or null if there isn't one. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val PREPARING_HINT_DELAY_MS = 2_500L

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanReadyPreview() {
    DeweyTheme {
        ScanContent(state = ScanUiState.Ready, onScan = {}, onSave = {}, onReset = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanPreparingPreview() {
    DeweyTheme {
        ScanContent(state = ScanUiState.Preparing, onScan = {}, onSave = {}, onReset = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanScannedPreview() {
    DeweyTheme {
        ScanContent(
            state = ScanUiState.Scanned(Uri.parse("content://app.dewey.scan/1")),
            onScan = {},
            onSave = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanSavingPreview() {
    DeweyTheme {
        ScanContent(state = ScanUiState.Saving, onScan = {}, onSave = {}, onReset = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanSavedPreview() {
    DeweyTheme {
        ScanContent(state = ScanUiState.Saved, onScan = {}, onSave = {}, onReset = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanSaveFailedPreview() {
    DeweyTheme {
        ScanContent(
            state = ScanUiState.SaveFailed("The scan is no longer available — scan again."),
            onScan = {},
            onSave = {},
            onReset = {},
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanUnavailablePreview() {
    DeweyTheme {
        ScanContent(state = ScanUiState.Unavailable, onScan = {}, onSave = {}, onReset = {})
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0F17, heightDp = 780, widthDp = 390)
@Composable
private fun ScanCancelledPreview() {
    DeweyTheme {
        ScanContent(state = ScanUiState.Cancelled, onScan = {}, onSave = {}, onReset = {})
    }
}

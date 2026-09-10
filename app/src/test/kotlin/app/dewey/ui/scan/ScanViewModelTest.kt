package app.dewey.ui.scan

import app.dewey.scan.DocumentScanner
import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.result.ActivityResult
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.Test

/**
 * A stand-in for [DocumentScannerEngine] that resolves synchronously and lets
 * a test choose exactly what the "device" does — succeed, refuse to start, or
 * hand back nothing — without any of ML Kit's or Play Services' real classes.
 */
private class FakeScanEngine : ScanEngine {

    var startCalls = 0
        private set

    /** What [startScan] does when called next; a test sets this up first. */
    var onStart: (onReady: (IntentSender) -> Unit, onUnavailable: () -> Unit) -> Unit =
        { _, onUnavailable -> onUnavailable() }

    /** What [outcome] returns, or throws, for the next call. */
    var onResult: (ActivityResult) -> DocumentScanner.Outcome = { DocumentScanner.Outcome.Cancelled }

    override fun startScan(activity: Activity, onReady: (IntentSender) -> Unit, onUnavailable: () -> Unit) {
        startCalls++
        onStart(onReady, onUnavailable)
    }

    override fun outcome(result: ActivityResult): DocumentScanner.Outcome = onResult(result)
}

/**
 * [ScanViewModel]'s state machine — see [ScanUiState] for what each step
 * means. Driven entirely through [FakeScanEngine], which resolves
 * synchronously, so every assertion here reads `state.value` directly with no
 * dispatcher or Robolectric involved.
 */
class ScanViewModelTest {

    private val activity = mockk<Activity>(relaxed = true)
    private val intentSender = mockk<IntentSender>(relaxed = true)
    private val pdfUri = mockk<Uri>(relaxed = true)
    private val activityResult = mockk<ActivityResult>(relaxed = true)

    @Test
    fun `starts out ready, before anything has been asked for`() {
        val viewModel = ScanViewModel(FakeScanEngine())

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Ready)
    }

    @Test
    fun `starting a scan moves to preparing while the engine is still resolving`() {
        val engine = FakeScanEngine().apply { onStart = { _, _ -> } } // never resolves
        val viewModel = ScanViewModel(engine)

        viewModel.startScan(activity)

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Preparing)
    }

    @Test
    fun `a second start while still preparing is ignored`() {
        val engine = FakeScanEngine().apply { onStart = { _, _ -> } } // never resolves
        val viewModel = ScanViewModel(engine)

        viewModel.startScan(activity)
        viewModel.startScan(activity)

        assertThat(engine.startCalls).isEqualTo(1)
    }

    @Test
    fun `the engine handing back an intent sender is ready to launch`() {
        val engine = FakeScanEngine().apply { onStart = { onReady, _ -> onReady(intentSender) } }
        val viewModel = ScanViewModel(engine)

        viewModel.startScan(activity)

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.ReadyToLaunch(intentSender))
    }

    @Test
    fun `a start while ready to launch is ignored, not a second scanner activity`() {
        val engine = FakeScanEngine().apply { onStart = { onReady, _ -> onReady(intentSender) } }
        val viewModel = ScanViewModel(engine)

        viewModel.startScan(activity)
        viewModel.startScan(activity)

        assertThat(engine.startCalls).isEqualTo(1)
    }

    @Test
    fun `the engine reporting unavailable does not look like a crash`() {
        val engine = FakeScanEngine().apply { onStart = { _, onUnavailable -> onUnavailable() } }
        val viewModel = ScanViewModel(engine)

        viewModel.startScan(activity)

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Unavailable)
    }

    @Test
    fun `starting again after unavailable is allowed`() {
        val engine = FakeScanEngine().apply { onStart = { _, onUnavailable -> onUnavailable() } }
        val viewModel = ScanViewModel(engine)

        viewModel.startScan(activity)
        viewModel.startScan(activity)

        assertThat(engine.startCalls).isEqualTo(2)
    }

    @Test
    fun `launching the pending intent sender moves to scanning`() {
        val engine = FakeScanEngine().apply { onStart = { onReady, _ -> onReady(intentSender) } }
        val viewModel = ScanViewModel(engine)
        viewModel.startScan(activity)

        viewModel.onScanLaunched()

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Scanning)
    }

    @Test
    fun `reporting the launch is a no-op outside ready-to-launch`() {
        val viewModel = ScanViewModel(FakeScanEngine())

        viewModel.onScanLaunched()

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Ready)
    }

    @Test
    fun `a finished scan carries the pdf uri forward`() {
        val engine = FakeScanEngine().apply { onResult = { DocumentScanner.Outcome.Scanned(pdfUri) } }
        val viewModel = ScanViewModel(engine)

        viewModel.onScanResult(activityResult)

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Scanned(pdfUri))
    }

    @Test
    fun `backing out of the scanner reads as cancelled, not failed`() {
        val engine = FakeScanEngine().apply { onResult = { DocumentScanner.Outcome.Cancelled } }
        val viewModel = ScanViewModel(engine)

        viewModel.onScanResult(activityResult)

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Cancelled)
    }

    @Test
    fun `a scan that finishes without a document is a failure, not a cancellation`() {
        // The two used to be the same value. A scan that genuinely failed told
        // the user they had backed out, so they would retry for ever while the
        // app insisted nothing was wrong.
        val engine = FakeScanEngine().apply {
            onResult = { DocumentScanner.Outcome.NoDocument("the scanner returned success with no PDF") }
        }
        val viewModel = ScanViewModel(engine)

        viewModel.onScanResult(activityResult)

        assertThat(viewModel.state.value)
            .isEqualTo(ScanUiState.Failed("The scan finished but the scanner returned success with no PDF."))
    }

    @Test
    fun `an engine that throws reading the result is reported, not left to crash`() {
        val engine = FakeScanEngine().apply {
            onResult = { throw IllegalStateException("no pdf on the result") }
        }
        val viewModel = ScanViewModel(engine)

        viewModel.onScanResult(activityResult)

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Failed("no pdf on the result"))
    }

    @Test
    fun `reset returns to ready from a finished scan, ready for another`() {
        val engine = FakeScanEngine().apply { onResult = { DocumentScanner.Outcome.Scanned(pdfUri) } }
        val viewModel = ScanViewModel(engine)
        viewModel.onScanResult(activityResult)

        viewModel.reset()

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Ready)
    }

    @Test
    fun `reset returns to ready from unavailable`() {
        val engine = FakeScanEngine().apply { onStart = { _, onUnavailable -> onUnavailable() } }
        val viewModel = ScanViewModel(engine)
        viewModel.startScan(activity)

        viewModel.reset()

        assertThat(viewModel.state.value).isEqualTo(ScanUiState.Ready)
    }
}

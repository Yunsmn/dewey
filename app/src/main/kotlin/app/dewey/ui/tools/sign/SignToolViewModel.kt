package app.dewey.ui.tools.sign

import app.dewey.ui.tools.readPageCount
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.Corner
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.renderPageForSignature
import app.dewey.pdf.stampSignature
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.toolFailureMessage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** How wide the placement preview is rendered — screen width, so dragging on it feels 1:1 with a finger. */
private const val DEFAULT_PREVIEW_LONG_EDGE_PX = 1080

/** What the sign screen has chosen so far, and how its run is going. */
data class SignUiState(
    val source: PickedFile? = null,
    val pageCount: Int? = null,
    val pageNumberText: String = "",
    val preview: Bitmap? = null,
    val previewLoading: Boolean = false,
    val signature: Bitmap? = null,
    val hasSavedSignature: Boolean = false,
    val placement: SignPlacement = SignPlacement(x = 0.34f, y = 0.74f, width = 0.32f, height = 0.12f),
    /** The last [CornerGrid][app.dewey.ui.tools.secure.CornerGrid] quick position chosen, so the grid can highlight it — a free-form drag leaves this stale, which is fine: it is a shortcut's own memory, not a live description of [placement]. */
    val lastCorner: Corner = Corner.BOTTOM_CENTER,
    val run: ToolRunState = ToolRunState.Idle,
) {
    /** Null for blank or non-numeric text, rather than 0 — a typo should not read as "page zero". */
    val pageNumber: Int? get() = pageNumberText.trim().toIntOrNull()

    val canRun: Boolean
        get() = canRunSign(source != null, signature != null, pageNumber, pageCount) && run !is ToolRunState.Running

    val suggestedFileName: String get() = derivedFileName(source?.name.orEmpty(), "signed")
}

/**
 * Draws a signature onto one page of a chosen PDF — see
 * [app.dewey.pdf.stampSignature] for how the position the user dragged it to
 * on the preview lands in the page's own space, rotation and all.
 *
 * @param filesDir where the last signature drawn is remembered — see
 *   [saveSignature] — injected rather than reached for through [toolkit],
 *   which has no notion of app-wide files, the same way [app.dewey.scan.DocumentScanner]
 *   is built from the screen's own `Context` rather than a shared toolkit.
 * @param previewLongEdgePx how large a page is rendered for the placement
 *   preview — the screen's own width in pixels, so a drag on the preview
 *   tracks the finger at roughly one screen pixel per gesture pixel.
 */
class SignToolViewModel(
    private val toolkit: PdfToolkit,
    private val filesDir: File,
    private val previewLongEdgePx: Int = DEFAULT_PREVIEW_LONG_EDGE_PX,
) : ViewModel() {

    private val _state = MutableStateFlow(SignUiState())
    val state: StateFlow<SignUiState> = _state.asStateFlow()
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val exists = withContext(Dispatchers.IO) { hasSavedSignature(filesDir) }
            _state.value = _state.value.copy(hasSavedSignature = exists)
        }
    }

    fun onSourcePicked(file: PickedFile) {
        _state.value = _state.value.copy(
            source = file,
            pageCount = null,
            pageNumberText = "",
            preview = null,
            run = ToolRunState.Idle,
        )
        loadPageCount(file)
    }

    fun onPageNumberChanged(text: String) {
        _state.value = _state.value.copy(pageNumberText = text)
        loadPreviewIfValid()
    }

    fun onStepPage(delta: Int) {
        val current = _state.value
        val count = current.pageCount ?: return
        val page = (current.pageNumber ?: count) + delta
        if (page !in 1..count) return
        onPageNumberChanged(page.toString())
    }

    /** Called once [SignaturePad] hands back a finished drawing. */
    fun onSignatureDrawn(bitmap: Bitmap) {
        applyNewSignature(bitmap)
        _state.value = _state.value.copy(hasSavedSignature = true)
        viewModelScope.launch { saveSignature(filesDir, bitmap) }
    }

    fun onUseSavedSignature() {
        viewModelScope.launch {
            val saved = loadSavedSignature(filesDir) ?: return@launch
            applyNewSignature(saved)
        }
    }

    fun onClearSignature() {
        _state.value = _state.value.copy(signature = null)
    }

    /** A drag on the placement preview, in fractions of that preview's own displayed size. */
    fun onDrag(deltaXFraction: Float, deltaYFraction: Float) {
        _state.value = _state.value.copy(
            placement = dragPlacement(_state.value.placement, deltaXFraction, deltaYFraction),
        )
    }

    fun onCornerChosen(corner: Corner) {
        _state.value = _state.value.copy(
            placement = cornerPlacement(_state.value.placement, corner),
            lastCorner = corner,
        )
    }

    fun onSizeChosen(sizeFraction: Float) {
        val current = _state.value
        val signature = current.signature ?: return
        _state.value = current.copy(
            placement = resizePlacement(
                current = current.placement,
                sizeFraction = sizeFraction,
                boxWidthPx = current.preview?.width?.toFloat() ?: 0f,
                boxHeightPx = current.preview?.height?.toFloat() ?: 0f,
                signatureWidthPx = signature.width,
                signatureHeightPx = signature.height,
            ),
        )
    }

    /** Called once the user has picked where to save — see [SignUiState.suggestedFileName]. */
    fun onDestinationChosen(target: Uri) {
        val current = _state.value
        val source = current.source ?: return
        val signature = current.signature ?: return
        val pageNumber = current.pageNumber ?: return
        if (!current.canRun) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val result = stampSignature(
                workspace = toolkit.workspace,
                resolver = toolkit.resolver,
                source = source.uri,
                target = target,
                sizeBytes = source.sizeBytes,
                pageIndex = pageNumber - 1,
                signature = signature,
                normalizedX = current.placement.x,
                normalizedY = current.placement.y,
                normalizedWidth = current.placement.width,
                normalizedHeight = current.placement.height,
            ).onFailure { toolkit.discardOutput(target) }.onSuccess { toolkit.recordOutput(target) }
            _state.value = _state.value.afterRun(result, target)
        }
    }

    fun reset() {
        _state.value = _state.value.copy(run = ToolRunState.Idle)
    }

    private fun applyNewSignature(bitmap: Bitmap) {
        val current = _state.value
        val preview = current.preview
        val placement = defaultPlacement(
            boxWidthPx = preview?.width?.toFloat() ?: 0f,
            boxHeightPx = preview?.height?.toFloat() ?: 0f,
            signatureWidthPx = bitmap.width,
            signatureHeightPx = bitmap.height,
        )
        _state.value = current.copy(signature = bitmap, placement = placement)
    }

    /**
     * Only applies the count if [file] is still the chosen one: a fast
     * second pick shouldn't have an earlier read's result land after it and
     * overwrite the newer file's still-loading state.
     */
    private fun loadPageCount(file: PickedFile) {
        viewModelScope.launch {
            val result = toolkit.readPageCount(file)
            if (_state.value.source?.uri != file.uri) return@launch
            _state.value = result.fold(
                onSuccess = { count -> _state.value.copy(pageCount = count, pageNumberText = count.toString()) },
                onFailure = { error -> _state.value.copy(run = ToolRunState.Failed(toolFailureMessage(error))) },
            )
            loadPreviewIfValid()
        }
    }

    private fun loadPreviewIfValid() {
        // One render at a time: a newer page (or no valid page at all) makes
        // the one in flight pointless, and must not leave its spinner behind.
        previewJob?.cancel()
        val current = _state.value
        val page = current.pageNumber
        val count = current.pageCount
        val source = current.source
        if (source == null || count == null || page == null || page !in 1..count) {
            if (current.previewLoading) _state.value = current.copy(previewLoading = false)
            return
        }

        previewJob = viewModelScope.launch {
            _state.value = _state.value.copy(previewLoading = true)
            val result = renderPageForSignature(
                resolver = toolkit.resolver,
                cacheDir = toolkit.cacheDir,
                source = source.uri,
                sizeBytes = source.sizeBytes,
                pageIndex = page - 1,
                longEdgePx = previewLongEdgePx,
            )
            // Both, not just the file: stepping pages quickly starts a render
            // per step, and a slow one for page 3 must not land after page 4's
            // and put the signature on a page the person isn't looking at.
            if (_state.value.source?.uri != source.uri || _state.value.pageNumber != page) return@launch
            _state.value = result.fold(
                onSuccess = { bitmap -> withPreviewApplied(bitmap) },
                onFailure = { error ->
                    _state.value.copy(previewLoading = false, run = ToolRunState.Failed(toolFailureMessage(error)))
                },
            )
        }
    }

    /** The new preview, plus a placement recomputed to match its aspect ratio if a signature is already chosen. */
    private fun withPreviewApplied(bitmap: Bitmap): SignUiState {
        val updated = _state.value.copy(preview = bitmap, previewLoading = false)
        val signature = updated.signature ?: return updated
        return updated.copy(
            placement = defaultPlacement(
                boxWidthPx = bitmap.width.toFloat(),
                boxHeightPx = bitmap.height.toFloat(),
                signatureWidthPx = signature.width,
                signatureHeightPx = signature.height,
            ),
        )
    }

    companion object {
        fun factory(toolkit: PdfToolkit, filesDir: File, previewLongEdgePx: Int = DEFAULT_PREVIEW_LONG_EDGE_PX) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SignToolViewModel(toolkit, filesDir, previewLongEdgePx) as T
            }
    }
}

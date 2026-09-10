package app.dewey.ui.tools.raster

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.RasterImageFormat
import app.dewey.pdf.RasterQuality
import app.dewey.ui.tools.PickedFile
import app.dewey.ui.tools.ToolRunState
import app.dewey.ui.tools.derivedFileName
import app.dewey.ui.tools.describe
import app.dewey.ui.tools.toolFailureMessage
import java.io.IOException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the PDF-to-images screen has chosen so far, and how its run is going. */
data class PdfToImagesUiState(
    val source: PickedFile? = null,
    val format: RasterImageFormat = RasterImageFormat.JPEG,
    val quality: RasterQuality = RasterQuality.BALANCED,
    val destination: Uri? = null,
    val destinationName: String? = null,
    val run: ToolRunState = ToolRunState.Idle,
) {
    val canRun: Boolean get() = canRunPdfToImages(hasSource = source != null, hasDestination = destination != null)
}

/**
 * Renders every page of a chosen PDF to its own image file inside a folder
 * the user picks.
 *
 * Writing a page is this class's job, not [app.dewey.pdf.RasterTools]'s: the
 * engine only knows how to turn a page into bytes, one at a time, and hands
 * each to [writePage] here — see the constraint on [PdfToImagesUiState.destination]
 * being a SAF *tree* URI, which has to be converted to a document URI before
 * `createDocument` will accept it (a bug this app has already shipped once,
 * per [app.dewey.sort.DocumentMover]'s own notes on the distinction).
 */
class PdfToImagesViewModel(private val toolkit: PdfToolkit) : ViewModel() {

    private val _state = MutableStateFlow(PdfToImagesUiState())
    val state: StateFlow<PdfToImagesUiState> = _state.asStateFlow()

    fun onSourcePicked(file: PickedFile) {
        _state.value = _state.value.copy(source = file, run = ToolRunState.Idle)
    }

    fun onFormatChosen(format: RasterImageFormat) {
        _state.value = _state.value.copy(format = format)
    }

    fun onQualityChosen(quality: RasterQuality) {
        _state.value = _state.value.copy(quality = quality)
    }

    fun onDestinationPicked(tree: Uri) {
        val name = toolkit.resolver.describe(tree).name.ifEmpty { "Chosen folder" }
        _state.value = _state.value.copy(destination = tree, destinationName = name)
    }

    fun run() {
        val current = _state.value
        val source = current.source ?: return
        val destination = current.destination ?: return
        if (current.run is ToolRunState.Running) return

        _state.value = current.copy(run = ToolRunState.Running)
        viewModelScope.launch {
            val outcome = toolkit.raster.pdfToImages(
                uri = source.uri,
                sizeBytes = source.sizeBytes,
                format = current.format,
                quality = current.quality,
            ) { index, bytes ->
                writePage(toolkit.resolver, destination, current.format, source.name, index, bytes)
            }

            _state.value = _state.value.copy(
                run = outcome.fold(
                    onSuccess = { export -> ToolRunState.Done(pdfToImagesSummary(export)) },
                    onFailure = { error -> ToolRunState.Failed(toolFailureMessage(error)) },
                ),
            )
        }
    }

    fun reset() {
        _state.value = _state.value.copy(run = ToolRunState.Idle)
    }

    companion object {
        fun factory(toolkit: PdfToolkit) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PdfToImagesViewModel(toolkit) as T
        }
    }
}

/**
 * Writes one rendered page into [folderTree].
 *
 * [folderTree] is the tree URI [app.dewey.ui.tools.rememberFolderPicker] hands
 * back — not a document URI — so it is converted with
 * `buildDocumentUriUsingTree(tree, getTreeDocumentId(tree))` before
 * `createDocument` will accept it as a parent; handing the bare tree URI
 * straight to `createDocument` fails with "Invalid URI", the same trap
 * [app.dewey.sort.DocumentMover.asDocumentUri] documents on the move side.
 *
 * Throws rather than returning a status: [app.dewey.pdf.RasterTools.pdfToImages]
 * treats any exception out of its `onPage` callback as
 * [app.dewey.pdf.RasterFailure.WriteFailed] for the whole run, which is the
 * right outcome for a page that silently failed to save — a page skipped by
 * the renderer is one thing; one this function could not write is another,
 * and neither should be reported as if every page made it.
 */
private suspend fun writePage(
    resolver: ContentResolver,
    folderTree: Uri,
    format: RasterImageFormat,
    sourceName: String,
    pageIndex: Int,
    bytes: ByteArray,
) {
    val parentDocument = DocumentsContract.buildDocumentUriUsingTree(
        folderTree,
        DocumentsContract.getTreeDocumentId(folderTree),
    )
    val name = derivedFileName(sourceName, "page-${pageIndex + 1}", format.extension)
    val created = DocumentsContract.createDocument(resolver, parentDocument, format.mimeType, name)
        ?: throw IOException("could not create $name in $folderTree")

    resolver.openOutputStream(created)?.use { it.write(bytes) }
        ?: throw IOException("could not open $created for writing")
}

private val RasterImageFormat.mimeType: String
    get() = when (this) {
        RasterImageFormat.JPEG -> "image/jpeg"
        RasterImageFormat.PNG -> "image/png"
    }

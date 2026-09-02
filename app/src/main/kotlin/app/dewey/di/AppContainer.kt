package app.dewey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.dewey.data.db.DeweyDatabase
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.storage.DocumentTreeStore
import app.dewey.data.storage.SafDocumentSource
import app.dewey.index.Chunker
import app.dewey.index.Embedder
import app.dewey.index.OcrTextExtractor
import app.dewey.index.OnnxEmbedder
import app.dewey.index.PdfTextExtractor
import app.dewey.index.DocumentSearch
import app.dewey.work.TaskNotifications
import app.dewey.work.TaskRunner
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

private val Context.preferencesStore: DataStore<Preferences> by preferencesDataStore(name = "dewey")

/**
 * Manual dependency wiring, application-scoped.
 *
 * Deliberately not Hilt. The graph is small and entirely singleton, so a
 * container of `by lazy` properties expresses it with no annotation processor,
 * no generated code, and no indirection between asking for a thing and seeing
 * where it comes from. Revisit if the graph grows scopes.
 */
class AppContainer(private val context: Context) {

    init {
        // PDFBox-Android reads its fonts and glyph tables from assets, and will
        // fail obscurely on first use if this never ran.
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private val database: DeweyDatabase by lazy { DeweyDatabase.open(context) }

    val safDocumentSource: SafDocumentSource by lazy { SafDocumentSource(context) }

    val documentTreeStore: DocumentTreeStore by lazy {
        DocumentTreeStore(context.contentResolver, context.preferencesStore)
    }

    val taskNotifications: TaskNotifications by lazy { TaskNotifications(context) }

    val taskRunner: TaskRunner by lazy { TaskRunner(context) }

    val embedder: Embedder by lazy { embedderFactory() }

    val documentRepository: DocumentRepository by lazy {
        DocumentRepository(
            documentDao = database.documentDao(),
            chunkDao = database.chunkDao(),
            pdfText = PdfTextExtractor(context.contentResolver),
            ocrText = OcrTextExtractor(context.contentResolver),
            chunker = Chunker(),
            embedder = embedder,
        )
    }

    val documentSearch: DocumentSearch by lazy { DocumentSearch(database.chunkDao()) }

    /**
     * Overridable so instrumentation tests can index without loading a 120MB
     * encoder. Assigned before first use of [embedder] or it has no effect.
     */
    var embedderFactory: () -> Embedder = { OnnxEmbedder.create(context) }
}

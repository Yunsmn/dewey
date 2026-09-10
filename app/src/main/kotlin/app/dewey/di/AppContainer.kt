package app.dewey.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import app.dewey.BuildConfig
import app.dewey.classify.DocumentClassifier
import app.dewey.billing.Entitlements
import app.dewey.cloud.AnswerComposer
import app.dewey.cloud.GeminiAnswerComposer
import app.dewey.cloud.UnconfiguredAnswerComposer
import app.dewey.data.db.DeweyDatabase
import app.dewey.data.recent.RecentFiles
import app.dewey.data.recent.recentFilesStore
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.repository.NotesRepository
import app.dewey.data.storage.DocumentTreeStore
import app.dewey.data.storage.SafDocumentSource
import app.dewey.index.Chunker
import app.dewey.index.Embedder
import app.dewey.index.OcrTextExtractor
import app.dewey.index.OnnxEmbedder
import app.dewey.sort.DocumentMover
import app.dewey.sort.UndoLog
import app.dewey.index.PdfTextExtractor
import app.dewey.index.DocumentSearch
import app.dewey.pdf.PdfToolkit
import app.dewey.pdf.PdfWorkspace
import app.dewey.pdf.RasterTools
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
            // cacheDir is passed because PDFBox spills parsed state to a temp
            // file; the JVM default temp dir is not reliably writable on Android.
            pdfText = PdfTextExtractor(context.contentResolver, cacheDir = context.cacheDir),
            // Same cacheDir: a provider that streams hands back a descriptor
            // PdfRenderer cannot seek, and the fallback copies the file locally.
            ocrText = OcrTextExtractor(context.contentResolver, cacheDir = context.cacheDir),
            chunker = Chunker(),
            embedder = { embedder },
        )
    }

    val documentSearch: DocumentSearch by lazy { DocumentSearch(database.chunkDao()) }

    /** Notes, standalone or attached to a bill - see app.dewey.domain.model.Note. */
    val notesRepository: NotesRepository by lazy { NotesRepository(noteDao = database.noteDao()) }

    val documentDao by lazy { database.documentDao() }

    val classifier: DocumentClassifier by lazy { DocumentClassifier(embedder) }

    val documentMover: DocumentMover by lazy { DocumentMover(context.contentResolver) }

    val undoLog: UndoLog by lazy { UndoLog(java.io.File(context.filesDir, "sort")) }

    /**
     * [GeminiAnswerComposer] when a Firebase project was configured at build
     * time, [UnconfiguredAnswerComposer] otherwise. BuildConfig.HAS_FIREBASE is
     * generated from whether `app/google-services.json` existed when Gradle
     * ran — see the comment on `hasFirebase` in app/build.gradle.kts — so this
     * is the one place that decision reaches the running app.
     */
    val answerComposer: AnswerComposer by lazy {
        if (BuildConfig.HAS_FIREBASE) GeminiAnswerComposer() else UnconfiguredAnswerComposer()
    }

    /** Whether the paid tier is available — see [app.dewey.billing.Entitlements]. */
    val entitlements: Entitlements by lazy { Entitlements(context) }

    /** Scans and tool results, newest first, for Home — see [RecentFiles]. */
    val recentFiles: RecentFiles by lazy { RecentFiles(context.recentFilesStore, context.contentResolver) }

    /** The PDF tools' shared dependencies — see [PdfToolkit]. */
    val pdfToolkit: PdfToolkit by lazy {
        val workspace = PdfWorkspace(context.contentResolver, context.cacheDir)
        PdfToolkit(
            resolver = context.contentResolver,
            cacheDir = context.cacheDir,
            workspace = workspace,
            raster = RasterTools(context.contentResolver, workspace, context.cacheDir),
            recents = recentFiles,
        )
    }

    /**
     * Overridable so instrumentation tests can index without loading a 118MB
     * encoder. Assigned before first use of [embedder] or it has no effect.
     */
    var embedderFactory: () -> Embedder = { OnnxEmbedder.create(context) }
}

package app.dewey.pipeline

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.dewey.data.db.DeweyDatabase
import app.dewey.data.repository.DocumentRepository
import app.dewey.data.storage.SafDocument
import app.dewey.domain.model.TextSource
import app.dewey.index.Chunker
import app.dewey.index.DocumentSearch
import app.dewey.index.OnnxEmbedder
import app.dewey.index.OcrTextExtractor
import app.dewey.index.PdfTextExtractor
import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The whole of stage 1, on a device, against real documents.
 *
 * Every component has its own test; this one checks they compose. A PDF goes in
 * and a search result comes out, through text extraction, chunking, on-device
 * embedding, storage, and hybrid retrieval — with French, Arabic and English
 * documents in the same index, because that is the corpus this has to survive.
 */
@RunWith(AndroidJUnit4::class)
class IndexPipelineTest {

    private lateinit var context: Context

    /**
     * The test APK's own context. The bundled corpus lives in the test
     * application's assets, not the app under test's — reading it from
     * targetContext finds only the app's own models directory.
     */
    private lateinit var testAssets: Context
    private lateinit var database: DeweyDatabase
    private lateinit var repository: DocumentRepository
    private lateinit var search: DocumentSearch
    private lateinit var embedder: OnnxEmbedder

    private val documents = listOf(
        "document (5).pdf",                        // Lydec bill, French
        "photo_7075@20220406.pdf",                 // ONEE bill, Arabic
        "file-825778.pdf",                         // Attijariwafa statement, French
        "IMG_9338.pdf",                            // Marjane receipt, English
        "WhatsApp Doc 2023-10-22 at 07.05.19.pdf", // Clinic report, Arabic
    )

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testAssets = InstrumentationRegistry.getInstrumentation().context
        PDFBoxResourceLoader.init(context)

        database = Room.inMemoryDatabaseBuilder(context, DeweyDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        embedder = OnnxEmbedder.create(context)
        repository = DocumentRepository(
            documentDao = database.documentDao(),
            chunkDao = database.chunkDao(),
            pdfText = PdfTextExtractor(context.contentResolver, cacheDir = context.cacheDir),
            ocrText = OcrTextExtractor(context.contentResolver, cacheDir = context.cacheDir),
            chunker = Chunker(),
            embedder = { embedder },
        )
        search = DocumentSearch(database.chunkDao())
    }

    @After
    fun tearDown() {
        database.close()
        embedder.close()
    }

    /** Copies a bundled PDF somewhere the app can open by URI. */
    private fun stage(name: String): SafDocument {
        val file = File(context.cacheDir, name)
        testAssets.assets.open("corpus/$name").use { input ->
            file.outputStream().use(input::copyTo)
        }
        // Not a real SAF tree — this pipeline test stages files by plain path —
        // so the parent fields are a placeholder rather than a meaningful folder.
        val parent = Uri.fromFile(context.cacheDir)
        return SafDocument(
            uri = Uri.fromFile(file),
            documentId = name,
            parentDocumentId = context.cacheDir.name,
            parentUri = parent,
            displayName = name,
            mimeType = "application/pdf",
            sizeBytes = file.length(),
            lastModified = file.lastModified(),
        )
    }

    private fun indexAll() = runBlocking {
        documents.map { repository.importAndIndex(stage(it)) }
    }

    @Test
    fun indexesEveryDocumentAndExtractsItsText() {
        val indexed = indexAll()

        assertThat(indexed).hasSize(documents.size)
        indexed.forEach { document ->
            assertThat(document.isIndexed).isTrue()
            // These carry real text layers, so none should have needed OCR.
            assertThat(document.textSource).isEqualTo(TextSource.EMBEDDED)
        }
    }

    @Test
    fun storesChunksForEveryDocument() = runBlocking {
        indexAll()

        assertThat(database.chunkDao().count()).isAtLeast(documents.size)
    }

    @Test
    fun findsTheFrenchBillFromAnEnglishDescription() = runBlocking {
        indexAll()

        val hits = search.search(
            queryText = "Lydec electricity and water bill",
            queryVector = embedder.embedQuery("Lydec electricity and water bill"),
        )

        assertThat(hits).isNotEmpty()
        val top = repository.byId(hits.first().documentId)!!
        assertThat(top.displayName).isEqualTo("document (5).pdf")
    }

    @Test
    fun findsTheArabicBillFromAnEnglishDescription() = runBlocking {
        indexAll()

        // The point of the multilingual encoder: a question in one language
        // reaching a document written in another.
        val hits = search.search(
            queryText = "ONEE national electricity office bill",
            queryVector = embedder.embedQuery("ONEE national electricity office bill"),
        )

        assertThat(hits).isNotEmpty()
        val top = repository.byId(hits.first().documentId)!!
        assertThat(top.displayName).isEqualTo("photo_7075@20220406.pdf")
    }

    @Test
    fun findsTheBankStatementRatherThanTheBill() = runBlocking {
        indexAll()

        val hits = search.search(
            queryText = "Attijariwafa Bank account statement",
            queryVector = embedder.embedQuery("Attijariwafa Bank account statement"),
        )

        val top = repository.byId(hits.first().documentId)!!
        assertThat(top.displayName).isEqualTo("file-825778.pdf")
    }

    @Test
    fun reindexingAnUnchangedDocumentDoesNotDuplicateIt() = runBlocking {
        indexAll()
        val afterFirst = database.chunkDao().count()

        indexAll()

        // Rescanning a folder must cost nothing for files that have not changed,
        // or a second scan of four hundred documents redoes all the work.
        assertThat(database.chunkDao().count()).isEqualTo(afterFirst)
        assertThat(repository.observeDocumentsCount()).isEqualTo(documents.size)
    }

    @Test
    fun searchingAnEmptyIndexReturnsNothing() = runBlocking {
        val hits = search.search("anything at all", embedder.embedQuery("anything at all"))

        assertThat(hits).isEmpty()
    }

    private suspend fun DocumentRepository.observeDocumentsCount(): Int =
        observeDocuments().first().size
}

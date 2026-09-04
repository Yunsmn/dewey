package app.dewey.data.repository

import app.dewey.data.db.ChunkDao
import app.dewey.data.db.ChunkRow
import app.dewey.data.db.DocumentDao
import app.dewey.data.db.DocumentRow
import app.dewey.data.db.FloatArrayCodec
import app.dewey.data.storage.SafDocument
import app.dewey.domain.model.DocType
import app.dewey.domain.model.Document
import app.dewey.domain.model.TextSource
import app.dewey.index.Chunker
import app.dewey.index.Embedder
import app.dewey.index.OcrTextExtractor
import app.dewey.index.PdfTextExtractor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Turns a file the user granted us into something searchable.
 *
 * The pipeline is: read text, chunk it, embed the chunks, store all of it. Each
 * document is embedded exactly once, here, at import — never per query. That is
 * the difference between search that feels instant and search that reruns a
 * neural network every time someone types.
 */
class DocumentRepository(
    private val documentDao: DocumentDao,
    private val chunkDao: ChunkDao,
    private val pdfText: PdfTextExtractor,
    private val ocrText: OcrTextExtractor,
    private val chunker: Chunker,
    private val embedder: Embedder,
) {

    fun observeDocuments(): Flow<List<Document>> =
        documentDao.observeAll().map { rows -> rows.map(DocumentRow::toDomain) }

    fun observeCount(): Flow<Int> = documentDao.observeCount()

    /** Documents the classifier declined to file, least confident first. */
    fun observeNeedingReview(): Flow<List<Document>> =
        documentDao.observeNeedingReview().map { rows -> rows.map(DocumentRow::toDomain) }

    suspend fun byId(id: Long): Document? = documentDao.byId(id)?.toDomain()

    /**
     * Imports a document and builds its index entry.
     *
     * Skips work when the file is unchanged: re-running a scan over a folder
     * where three files were added should cost three documents of effort, not
     * four hundred. Size and modification time are what SAF gives us cheaply,
     * and together they are a good enough change signal for this.
     */
    suspend fun importAndIndex(source: SafDocument): Document {
        val existing = documentDao.byUri(source.uri.toString())
        if (existing != null && existing.isUnchangedFrom(source) && existing.indexedAt != null) {
            return existing.toDomain()
        }

        val extraction = extractText(source)
        val row = DocumentRow(
            id = existing?.id ?: 0,
            uri = source.uri.toString(),
            displayName = source.displayName,
            sizeBytes = source.sizeBytes,
            lastModified = source.lastModified,
            pageCount = extraction.pageCount,
            docType = (existing?.docType ?: DocType.UNKNOWN.name),
            language = existing?.language,
            textSource = extraction.source.name,
            text = extraction.text,
            indexedAt = null,
        )
        val documentId = documentDao.upsert(row).let { if (it == -1L) existing!!.id else it }

        val text = extraction.text
        if (!text.isNullOrBlank()) {
            indexChunks(documentId, text)
        } else {
            // Nothing to search on, but the document still belongs in the library
            // and in the review queue. Clearing stale chunks matters when a file
            // was replaced by an unreadable version.
            chunkDao.deleteForDocument(documentId)
        }

        documentDao.markIndexed(documentId, System.currentTimeMillis())
        return documentDao.byId(documentId)!!.toDomain()
    }

    private suspend fun indexChunks(documentId: Long, text: String) {
        val passages = chunker.chunk(text)
        if (passages.isEmpty()) {
            chunkDao.deleteForDocument(documentId)
            return
        }

        val vectors = embedder.embedPassages(passages)
        check(vectors.size == passages.size) {
            "Embedder returned ${vectors.size} vectors for ${passages.size} passages"
        }

        val rows = passages.mapIndexed { ordinal, passage ->
            ChunkRow(
                documentId = documentId,
                ordinal = ordinal,
                text = passage,
                embedding = FloatArrayCodec.encode(vectors[ordinal]),
            )
        }
        chunkDao.replaceForDocument(documentId, rows)
    }

    private data class Extraction(val text: String?, val source: TextSource, val pageCount: Int)

    /**
     * Prefers the PDF's own text layer and falls back to OCR.
     *
     * Order matters for both speed and accuracy: an embedded text layer is exact
     * and nearly free, while OCR is slow and approximate. Scanned documents are
     * common enough in a real archive that the fallback is not an edge case, but
     * running it on files that do not need it would make indexing unusable.
     */
    private suspend fun extractText(source: SafDocument): Extraction {
        val embedded = pdfText.extract(source.uri, source.sizeBytes)
        if (embedded != null && embedded.text.isNotBlank()) {
            return Extraction(embedded.text, TextSource.EMBEDDED, embedded.pageCount)
        }

        val ocr = ocrText.extract(source.uri)
        if (!ocr.isNullOrBlank()) {
            return Extraction(ocr, TextSource.OCR, embedded?.pageCount ?: 0)
        }

        return Extraction(null, TextSource.FAILED, embedded?.pageCount ?: 0)
    }

    private fun DocumentRow.isUnchangedFrom(source: SafDocument): Boolean =
        sizeBytes == source.sizeBytes && lastModified == source.lastModified
}

private fun DocumentRow.toDomain(): Document = Document(
    id = id,
    uri = uri,
    displayName = displayName,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    pageCount = pageCount,
    docType = runCatching { DocType.valueOf(docType) }.getOrDefault(DocType.UNKNOWN),
    language = language,
    textSource = runCatching { TextSource.valueOf(textSource) }.getOrDefault(TextSource.NONE),
    indexedAt = indexedAt,
)

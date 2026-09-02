package app.dewey.index

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer

/**
 * multilingual-e5-small, running on device through ONNX Runtime.
 *
 * Nothing here reaches the network. Documents become vectors on the phone, which
 * is what lets the app say that only retrieved passages are ever sent anywhere.
 *
 * The model is int8-quantised and multilingual because the corpus is French,
 * Arabic and English, often within one document — an English-only encoder would
 * fail most of it.
 */
class OnnxEmbedder private constructor(
    private val session: OrtSession,
    private val environment: OrtEnvironment,
    private val tokenizer: SentencePieceTokenizer,
    private val io: CoroutineDispatcher,
) : Embedder {

    override val dimensions: Int = DIMENSIONS

    // ONNX sessions are not safe to run concurrently. Indexing and a user's
    // query can arrive together, and without this they corrupt each other's
    // output tensors rather than merely contending.
    private val lock = Mutex()

    private val inputNames: Set<String> = session.inputNames.toSet()

    /**
     * e5 was trained with `passage:` and `query:` prefixes and expects them.
     * Dropping them costs several points of retrieval accuracy for no saving.
     */
    override suspend fun embedPassages(texts: List<String>): List<FloatArray> =
        embed(texts.map { "passage: $it" })

    override suspend fun embedQuery(text: String): FloatArray =
        embed(listOf("query: $text")).first()

    private suspend fun embed(texts: List<String>): List<FloatArray> = withContext(io) {
        if (texts.isEmpty()) return@withContext emptyList()

        val results = ArrayList<FloatArray>(texts.size)
        for (start in texts.indices step BATCH_SIZE) {
            val batch = texts.subList(start, minOf(start + BATCH_SIZE, texts.size))
            results += lock.withLock { runBatch(batch) }
        }
        results
    }

    private fun runBatch(texts: List<String>): List<FloatArray> {
        val encoded = texts.map { tokenizer.encode(it) }
        val width = encoded.maxOf { it.size }
        val rows = encoded.size

        val ids = LongArray(rows * width)
        val mask = LongArray(rows * width)
        for (row in encoded.indices) {
            val tokens = encoded[row]
            for (column in tokens.indices) {
                ids[row * width + column] = tokens[column].toLong()
                mask[row * width + column] = 1L
            }
        }

        val shape = longArrayOf(rows.toLong(), width.toLong())
        val inputs = HashMap<String, OnnxTensor>()

        // Tensors hold native memory, so every one created must be closed even if
        // the run throws partway through building the map.
        try {
            inputs["input_ids"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), shape)
            inputs["attention_mask"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(mask), shape)
            if ("token_type_ids" in inputNames) {
                inputs["token_type_ids"] =
                    OnnxTensor.createTensor(environment, LongBuffer.wrap(LongArray(rows * width)), shape)
            }

            session.run(inputs).use { output ->
                @Suppress("UNCHECKED_CAST")
                val hidden = output[0].value as Array<Array<FloatArray>>
                return hidden.mapIndexed { row, tokens -> pool(tokens, mask, row, width) }
            }
        } finally {
            inputs.values.forEach { runCatching { it.close() } }
        }
    }

    /**
     * Mean-pools token vectors over real tokens only.
     *
     * Padding must be excluded. Averaging it in pulls every long text toward the
     * same point, which shows up as unrelated long documents all scoring alike.
     */
    private fun pool(tokens: Array<FloatArray>, mask: LongArray, row: Int, width: Int): FloatArray {
        val pooled = FloatArray(DIMENSIONS)
        var counted = 0

        for (column in 0 until minOf(width, tokens.size)) {
            if (mask[row * width + column] == 0L) continue
            val vector = tokens[column]
            for (index in 0 until minOf(DIMENSIONS, vector.size)) pooled[index] += vector[index]
            counted++
        }

        if (counted > 0) {
            val scale = 1f / counted
            for (index in pooled.indices) pooled[index] *= scale
        }
        return VectorMath.normalise(pooled)
    }

    override fun close() {
        runCatching { session.close() }
    }

    companion object {
        private const val TAG = "OnnxEmbedder"
        private const val DIMENSIONS = 384
        private const val BATCH_SIZE = 8

        private const val MODEL_ASSET = "models/encoder.onnx"
        private const val VOCAB_ASSET = "models/tokenizer.bin"

        /**
         * Loads the encoder, copying it out of the APK on first run.
         *
         * The asset is read from disk by the native runtime rather than handed
         * over as a byte array: the model is well over a hundred megabytes, and
         * materialising that on the Java heap is an OutOfMemoryError on the
         * devices most likely to be running this.
         */
        fun create(
            context: Context,
            io: CoroutineDispatcher = Dispatchers.Default,
        ): OnnxEmbedder {
            val modelFile = materialise(context, MODEL_ASSET, "encoder.onnx")

            val tokenizer = context.assets.open(VOCAB_ASSET).use(SentencePieceTokenizer::load)

            val environment = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                // More threads than physical cores makes this slower, not faster,
                // and starves the UI thread during a long indexing run.
                setIntraOpNumThreads(
                    Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
                )
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
            }
            val session = environment.createSession(modelFile.absolutePath, options)

            return OnnxEmbedder(session, environment, tokenizer, io)
        }

        private fun materialise(context: Context, asset: String, name: String): File {
            val destination = File(context.filesDir, "models/$name")
            if (destination.exists() && destination.length() > 0) return destination

            destination.parentFile?.mkdirs()
            // Written to a temporary name and renamed, so a copy interrupted by
            // the process dying does not leave a truncated model that loads and
            // then fails deep inside the runtime.
            val partial = File(destination.parentFile, "$name.partial")
            try {
                context.assets.open(asset).use { input ->
                    partial.outputStream().buffered().use(input::copyTo)
                }
                check(partial.renameTo(destination)) { "Could not move model into place" }
            } catch (e: Exception) {
                partial.delete()
                Log.e(TAG, "Could not unpack $asset", e)
                throw e
            }
            return destination
        }
    }
}

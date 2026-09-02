package app.dewey.sort

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * A record of every file the sort moved, so all of it can be put back.
 *
 * This is not an accuracy feature. Sorting is the moment a stranger hands the
 * app four hundred of their own documents and lets it rearrange them, and the
 * only honest answer to "what if it gets it wrong" is a button that undoes the
 * whole thing. Cheap to build, and the difference between a feature someone
 * tries and one they do not.
 *
 * Written after each move rather than at the end: a sort interrupted by the
 * process dying must still be undoable, and a log that only exists on success
 * is missing exactly when it is needed.
 */
class UndoLog(
    private val directory: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    @Serializable
    data class Move(
        val documentUri: String,
        val movedToUri: String,
        val originalParentUri: String,
        val targetParentUri: String,
        val displayName: String,
        val folderName: String,
    )

    @Serializable
    data class Batch(
        val id: String,
        val startedAt: Long,
        val rootUri: String,
        val moves: List<Move> = emptyList(),
    )

    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }
    private val lock = Mutex()

    private val file: File get() = File(directory, FILE_NAME)

    suspend fun begin(batchId: String, rootUri: String) = withContext(io) {
        lock.withLock {
            write(Batch(id = batchId, startedAt = System.currentTimeMillis(), rootUri = rootUri))
        }
    }

    suspend fun record(move: Move) = withContext(io) {
        lock.withLock {
            val current = read() ?: return@withLock
            write(current.copy(moves = current.moves + move))
        }
    }

    /** The most recent sort, if there is one still undoable. */
    suspend fun latest(): Batch? = withContext(io) {
        lock.withLock { read()?.takeIf { it.moves.isNotEmpty() } }
    }

    suspend fun clear() = withContext(io) {
        lock.withLock { runCatching { file.delete() } }
        Unit
    }

    private fun read(): Batch? =
        try {
            file.takeIf { it.exists() }?.readText()?.let { json.decodeFromString<Batch>(it) }
        } catch (e: Exception) {
            // A corrupt log must not break sorting. Losing the ability to undo
            // one batch is bad; refusing to sort at all is worse.
            Log.w(TAG, "Undo log unreadable; discarding", e)
            runCatching { file.delete() }
            null
        }

    private fun write(batch: Batch) {
        try {
            directory.mkdirs()
            // Written to a temporary file and renamed so an interrupted write
            // cannot leave a half-serialised log that reads as empty.
            val partial = File(directory, "$FILE_NAME.partial")
            partial.writeText(json.encodeToString(batch))
            if (!partial.renameTo(file)) {
                partial.copyTo(file, overwrite = true)
                partial.delete()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not write undo log", e)
        }
    }

    private companion object {
        const val TAG = "UndoLog"
        const val FILE_NAME = "last_sort.json"
    }
}

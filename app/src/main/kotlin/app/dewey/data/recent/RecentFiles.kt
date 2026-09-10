package app.dewey.data.recent

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** Where a recent file came from. */
enum class RecentKind { SCAN, TOOL }

/**
 * A file Dewey made: a scan, or a tool's result.
 *
 * [uri] is kept as a string so the list logic below stays plain Kotlin and can
 * be tested without Android.
 */
data class RecentFile(
    val uri: String,
    val name: String,
    val kind: RecentKind,
    val createdAt: Long,
)

/**
 * The files Dewey made most recently, newest first — what Home lists under
 * "Recent", the way a scanner app shows its last scans.
 *
 * Every file here came from a create-document dialog, whose grant lasts only
 * until the process dies. [record] makes the grant persistent so a row still
 * opens next week, and releases it again when the entry falls off the end:
 * Android caps how many persisted grants an app may hold, and a list that only
 * ever took them would eventually stop being able to take new ones.
 */
class RecentFiles(
    private val store: DataStore<Preferences>,
    private val resolver: ContentResolver,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    val recent: Flow<List<RecentFile>> = store.data.map { decodeRecent(it[KEY].orEmpty()) }

    /**
     * Adds [uri] at the top, replacing any older entry for the same file.
     *
     * Never throws. It runs right after a scan or a tool has saved its file,
     * and listing that file is a convenience: a storage error here must not
     * turn a successful save into a "couldn't save" message — the scanner
     * calls this inside its save's error handling — or, from a tool's
     * `onSuccess`, into an uncaught exception that takes the app down.
     */
    suspend fun record(uri: Uri, kind: RecentKind) = withContext(Dispatchers.IO) {
        quietly("record $uri") {
            keepAccess(uri)
            val entry = RecentFile(uri.toString(), displayName(uri), kind, clock())
            var dropped = emptyList<RecentFile>()
            store.edit { prefs ->
                val update = addRecent(decodeRecent(prefs[KEY].orEmpty()), entry, MAX_ENTRIES)
                dropped = update.dropped
                prefs[KEY] = encodeRecent(update.kept)
            }
            dropped.forEach { releaseAccess(Uri.parse(it.uri)) }
        }
    }

    /** Removes the entry for [uri] — for a row whose file has since been deleted or moved. Never throws, like [record]. */
    suspend fun forget(uri: String) = withContext(Dispatchers.IO) {
        quietly("forget $uri") {
            store.edit { prefs ->
                prefs[KEY] = encodeRecent(decodeRecent(prefs[KEY].orEmpty()).filterNot { it.uri == uri })
            }
            releaseAccess(Uri.parse(uri))
        }
    }

    private suspend fun quietly(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not $what", e)
        }
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    private fun keepAccess(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, ACCESS)
        } catch (e: SecurityException) {
            // Not every provider offers persistable grants. The entry is still
            // worth showing; it may just not open after a restart.
            Log.w(TAG, "No persistable grant for $uri", e)
        }
    }

    private fun releaseAccess(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, ACCESS)
        } catch (e: SecurityException) {
            Log.w(TAG, "No persisted grant to release for $uri", e)
        }
    }

    companion object {
        private const val TAG = "RecentFiles"
        private val KEY = stringPreferencesKey("recent_files")
        private const val ACCESS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        /** Enough to fill Home a few times over; small enough never to approach the grant cap. */
        const val MAX_ENTRIES = 20
    }
}

internal val Context.recentFilesStore: DataStore<Preferences> by preferencesDataStore(name = "recent_files")

/** The outcome of [addRecent]: the list to store, and entries that fell off the end. */
internal data class RecentUpdate(val kept: List<RecentFile>, val dropped: List<RecentFile>)

/** Puts [entry] first, drops any older entry for the same uri, and caps the list at [max]. */
internal fun addRecent(current: List<RecentFile>, entry: RecentFile, max: Int): RecentUpdate {
    val merged = listOf(entry) + current.filterNot { it.uri == entry.uri }
    return RecentUpdate(kept = merged.take(max), dropped = merged.drop(max))
}

/**
 * One entry per line: `createdAt TAB kind TAB uri TAB name`. The name goes last
 * and has any tab or newline flattened to a space, since it is the only field
 * the user chose; content URIs never contain either.
 */
internal fun encodeRecent(entries: List<RecentFile>): String =
    entries.joinToString("\n") { entry ->
        val name = entry.name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        "${entry.createdAt}\t${entry.kind.name}\t${entry.uri}\t$name"
    }

/** The inverse of [encodeRecent]. A malformed line is skipped rather than failing the whole list. */
internal fun decodeRecent(encoded: String): List<RecentFile> =
    encoded.lineSequence().mapNotNull { line ->
        val parts = line.split('\t', limit = 4)
        if (parts.size != 4) return@mapNotNull null
        val createdAt = parts[0].toLongOrNull() ?: return@mapNotNull null
        val kind = RecentKind.entries.firstOrNull { it.name == parts[1] } ?: return@mapNotNull null
        if (parts[2].isBlank()) return@mapNotNull null
        RecentFile(uri = parts[2], name = parts[3], kind = kind, createdAt = createdAt)
    }.toList()

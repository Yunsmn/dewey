package app.dewey.data.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Remembers which folders the user granted, and keeps those grants alive.
 *
 * A tree URI is worthless across restarts unless the persistable permission is
 * taken at the moment the picker returns; the grant is otherwise scoped to the
 * process. Taking it is the whole reason this class exists.
 */
class DocumentTreeStore(
    private val resolver: ContentResolver,
    private val preferences: DataStore<Preferences>,
) {

    val grantedTrees: Flow<List<Uri>> =
        preferences.data.map { prefs ->
            prefs[KEY_TREES].orEmpty().map(Uri::parse).filter(::stillGranted)
        }

    /**
     * Persists a tree the user just picked. Returns false if the system refused
     * to make the grant persistable, which happens on some providers and must
     * not be reported to the user as success.
     */
    suspend fun remember(treeUri: Uri): Boolean {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            resolver.takePersistableUriPermission(treeUri, flags)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not persist permission for $treeUri", e)
            return false
        }
        preferences.edit { prefs ->
            prefs[KEY_TREES] = prefs[KEY_TREES].orEmpty() + treeUri.toString()
        }
        return true
    }

    suspend fun forget(treeUri: Uri) {
        preferences.edit { prefs ->
            prefs[KEY_TREES] = prefs[KEY_TREES].orEmpty() - treeUri.toString()
        }
        runCatching {
            resolver.releasePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }

    /**
     * A grant can disappear without us being told — the user revokes it in
     * Settings, or an SD card is removed. Filtering on read means the UI never
     * offers a folder that would fail the moment it was opened.
     */
    private fun stillGranted(treeUri: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == treeUri && it.isReadPermission }

    private companion object {
        const val TAG = "DocumentTreeStore"
        val KEY_TREES = stringSetPreferencesKey("granted_trees")
    }
}

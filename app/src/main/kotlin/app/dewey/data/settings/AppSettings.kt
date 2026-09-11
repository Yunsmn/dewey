package app.dewey.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** How Dewey picks light or dark. [SYSTEM] follows the phone's own setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * [ThemeMode] for whatever string is stored, defaulting to [ThemeMode.SYSTEM]
 * for anything that isn't a recognised name — including nothing at all, which
 * is what a fresh install reads before anyone has touched the setting.
 */
internal fun themeModeFor(stored: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.name == stored } ?: ThemeMode.SYSTEM

/** The inverse of [themeModeFor] — kept a plain function so the round trip is what a test drives, with no [Preferences] involved. */
internal fun storedValueFor(mode: ThemeMode): String = mode.name

/**
 * Settings for the whole app, as opposed to the per-feature stores like
 * [app.dewey.assistant.AssistantQuota] or [app.dewey.data.recent.RecentFiles].
 *
 * Its own DataStore, the way each of those keeps its own, rather than a
 * shared "dewey" preferences file — a setting changed from the Me tab should
 * never share a write transaction with something unrelated that happens to
 * read preferences at the same time.
 */
class AppSettings(private val store: DataStore<Preferences>) {

    val themeMode: Flow<ThemeMode> = store.data.map { prefs -> themeModeFor(prefs[THEME_MODE_KEY]) }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { prefs -> prefs[THEME_MODE_KEY] = storedValueFor(mode) }
    }

    private companion object {
        val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
    }
}

/** Declared the way `Context.recentFilesStore` is — see [app.dewey.data.recent.RecentFiles]. */
internal val Context.appSettingsStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

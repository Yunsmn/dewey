package app.dewey.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.google.common.truth.Truth.assertThat
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * [themeModeFor] and [storedValueFor] are the whole round trip between a
 * [ThemeMode] and what gets persisted for it, kept as pure functions so a
 * test can drive every case with no [DataStore] involved.
 */
class ThemeModeMappingTest {

    @Test
    fun `themeModeFor reads back every stored name`() {
        ThemeMode.entries.forEach { mode ->
            assertThat(themeModeFor(storedValueFor(mode))).isEqualTo(mode)
        }
    }

    @Test
    fun `themeModeFor defaults to SYSTEM for null`() {
        assertThat(themeModeFor(null)).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `themeModeFor defaults to SYSTEM for an unrecognised value`() {
        assertThat(themeModeFor("sepia")).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `storedValueFor is the enum's own name`() {
        assertThat(storedValueFor(ThemeMode.LIGHT)).isEqualTo("LIGHT")
        assertThat(storedValueFor(ThemeMode.DARK)).isEqualTo("DARK")
        assertThat(storedValueFor(ThemeMode.SYSTEM)).isEqualTo("SYSTEM")
    }
}

/**
 * [AppSettings] wired to a real [DataStore] over a temp file — no Robolectric
 * and no [android.content.Context] needed, the same approach as
 * `AssistantQuotaTest`.
 */
class AppSettingsTest {

    private fun store(): DataStore<Preferences> {
        val file = File.createTempFile("app_settings", ".preferences_pb").apply { deleteOnExit() }
        return PreferenceDataStoreFactory.create(produceFile = { file })
    }

    @Test
    fun `themeMode defaults to SYSTEM before anything is set`() = runTest {
        val settings = AppSettings(store())

        assertThat(settings.themeMode.first()).isEqualTo(ThemeMode.SYSTEM)
    }

    @Test
    fun `setThemeMode is reflected by themeMode`() = runTest {
        val settings = AppSettings(store())

        settings.setThemeMode(ThemeMode.DARK)

        assertThat(settings.themeMode.first()).isEqualTo(ThemeMode.DARK)
    }

    @Test
    fun `setThemeMode overwrites a previous choice`() = runTest {
        val settings = AppSettings(store())
        settings.setThemeMode(ThemeMode.DARK)

        settings.setThemeMode(ThemeMode.LIGHT)

        assertThat(settings.themeMode.first()).isEqualTo(ThemeMode.LIGHT)
    }
}

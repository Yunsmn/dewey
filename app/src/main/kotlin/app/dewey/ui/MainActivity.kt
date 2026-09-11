package app.dewey.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.dewey.DeweyApplication
import app.dewey.data.settings.ThemeMode
import app.dewey.ui.theme.DeweyTheme
import app.dewey.widgets.refreshDeweyWidgets
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {

    /**
     * What a home-screen widget opened the app to do — scan, or show notes —
     * until [DeweyApp] has done it. Compose state so an intent arriving while
     * the app is already open, through [onNewIntent], reaches the running UI.
     */
    private var launchAction by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as DeweyApplication).container

        // A fresh launch only. After a rotation the activity still holds the
        // intent it started with, and acting on it again would reopen the
        // scanner every time the phone turns.
        if (savedInstanceState == null) launchAction = intent?.action

        // Read once, synchronously, before the first frame. Collecting the
        // Flow only inside Compose would draw that first frame in the
        // system's theme and then flip to a chosen Light or Dark a moment
        // later, which reads as a flash — this file is small and already on
        // disk by the time DataStore is asked for anything else, so the read
        // is not worth trading for a guaranteed-wrong first frame.
        val initialThemeMode = runBlocking { container.appSettings.themeMode.first() }

        setContent {
            val themeMode by container.appSettings.themeMode.collectAsStateWithLifecycle(initialValue = initialThemeMode)
            val dark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            DeweyTheme(dark = dark) {
                DeweyApp(
                    container = container,
                    launchAction = launchAction,
                    onLaunchActionHandled = { launchAction = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        launchAction = intent.action
    }

    override fun onStop() {
        super.onStop()
        // Widgets cannot be seen while the app is in front, so refreshing them
        // as it leaves covers every change made inside it — a note saved, a
        // sort that found new bills, a purchase that unlocked them — with one
        // call rather than one at every place data changes.
        lifecycleScope.launch { refreshDeweyWidgets(applicationContext) }
    }
}

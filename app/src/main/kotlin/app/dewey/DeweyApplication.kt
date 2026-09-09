package app.dewey

import android.app.Application
import androidx.work.Configuration
import app.dewey.di.AppContainer
import app.dewey.work.DeweyWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class DeweyApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    /**
     * Lives as long as the process. Entitlements outlive any screen — a
     * purchase made on the paywall has to be visible to the library behind it —
     * so the read that populates them cannot hang off a ViewModel's scope.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Before anything can ask whether a feature is unlocked. A paywall
        // opened from a cold start is exactly when that happens.
        container.entitlements.start(scope)
    }

    /**
     * WorkManager is configured here rather than by its default initialiser,
     * which the manifest removes. On-demand initialisation means the container
     * exists before any worker asks it for a dependency.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(DeweyWorkerFactory(container))
            .build()
}

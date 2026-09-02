package app.dewey

import android.app.Application
import androidx.work.Configuration
import app.dewey.di.AppContainer
import app.dewey.work.DeweyWorkerFactory

class DeweyApplication : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
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

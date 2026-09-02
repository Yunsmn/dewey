package app.dewey.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import app.dewey.di.AppContainer

/**
 * Constructs workers with their dependencies.
 *
 * WorkManager instantiates workers reflectively and only ever passes a Context
 * and parameters, so without a factory a worker can reach its collaborators only
 * through global state. This keeps the wiring explicit and lets tests hand a
 * worker fakes.
 */
class DeweyWorkerFactory(private val container: AppContainer) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        IndexWorker::class.java.name -> IndexWorker(
            context = appContext,
            params = workerParameters,
            source = container.safDocumentSource,
            repository = container.documentRepository,
            notifications = container.taskNotifications,
        )
        SortWorker::class.java.name -> SortWorker(
            context = appContext,
            params = workerParameters,
            source = container.safDocumentSource,
            documentDao = container.documentDao,
            classifier = container.classifier,
            mover = container.documentMover,
            undoLog = container.undoLog,
            notifications = container.taskNotifications,
        )
        UndoSortWorker::class.java.name -> UndoSortWorker(
            context = appContext,
            params = workerParameters,
            documentDao = container.documentDao,
            mover = container.documentMover,
            undoLog = container.undoLog,
            notifications = container.taskNotifications,
        )
        // Returning null hands the class back to the default factory rather than
        // failing, so an unrecognised worker is not a crash.
        else -> null
    }
}

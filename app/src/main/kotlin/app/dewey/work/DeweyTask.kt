package app.dewey.work

/**
 * The long-running jobs the app owns.
 *
 * Each has a stable unique name so WorkManager can enforce one-at-a-time per
 * kind across process death. The name is the identity: relaunching the app
 * mid-index must find the running job, not start a second one.
 */
enum class DeweyTask(val uniqueName: String) {
    INDEX("dewey.task.index"),
    SORT("dewey.task.sort"),
    UNDO("dewey.task.undo"),
    ;

    companion object {
        fun fromUniqueName(name: String): DeweyTask? = entries.find { it.uniqueName == name }
    }
}

/**
 * What a task is doing, as the UI needs to see it.
 *
 * Deliberately a snapshot rather than a stream of events: a screen that
 * subscribes halfway through a four-hundred-file sort needs the current state
 * immediately, not the events it missed.
 */
sealed interface TaskState {

    data object Idle : TaskState

    data class Running(
        val completed: Int,
        val total: Int,
        val currentItem: String?,
    ) : TaskState {
        val fraction: Float
            get() = if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)
    }

    data class Finished(val processed: Int, val failed: Int) : TaskState

    data class Failed(val message: String) : TaskState

    data object Cancelled : TaskState
}

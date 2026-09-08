package app.dewey.ui.library

import app.dewey.work.TaskState

/**
 * Which of the two tasks the single banner should be showing.
 *
 * The screen has room for one banner and two things that can fill it. The rule
 * used to be "the index task unless it is Idle", which is wrong in a way that
 * hid the app's best sentence: an index that has finished is not Idle, it is
 * Finished, and it stays Finished. Since you must index before you can sort,
 * the index banner won every time and "Filed 96 documents into 11 folders"
 * could never appear.
 *
 * The rule instead:
 *
 *  - a task that is running always wins, because it is the one with a
 *    progress bar and a Stop button attached to it
 *  - otherwise the newer piece of news wins, which [lastSettled] names
 *  - a task that has never run says nothing
 *
 * Ordering is passed in rather than inferred from the states themselves. Both
 * orders really happen — sort after index, and index again after a sort when a
 * second folder is added — and nothing in a settled [TaskState] says when it
 * settled.
 */
internal fun bannerTask(
    indexTask: TaskState,
    sortTask: TaskState,
    lastSettled: BannerSource?,
): TaskState = when {
    indexTask is TaskState.Running -> indexTask
    sortTask is TaskState.Running -> sortTask

    lastSettled == BannerSource.SORT && sortTask !is TaskState.Idle -> sortTask
    lastSettled == BannerSource.INDEX && indexTask !is TaskState.Idle -> indexTask

    // Nothing has settled during this screen's life — after a process restart,
    // say, where WorkManager still remembers a finished job. Either is honest;
    // prefer the sort, since it is the later step of the two.
    sortTask !is TaskState.Idle -> sortTask
    else -> indexTask
}

/** Which task most recently finished. */
internal enum class BannerSource { INDEX, SORT }

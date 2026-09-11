package app.dewey.widgets

import android.content.Context
import android.content.Intent
import app.dewey.ui.MainActivity

/**
 * The two things a widget tap can ask the app to open.
 *
 * MainActivity reads one of these off the launching intent's action and
 * navigates accordingly - see its own code for how. A widget only ever needs
 * to know the string, never the destination's route or nav graph.
 */
object WidgetActions {
    const val OPEN_SCAN = "app.dewey.action.OPEN_SCAN"
    const val OPEN_NOTES = "app.dewey.action.OPEN_NOTES"
}

/**
 * The intent every widget tap launches: MainActivity, stamped with [action].
 *
 * NEW_TASK plus CLEAR_TOP so tapping a widget always lands on a single,
 * top-most instance of the app rather than stacking a second one on top of
 * whatever the launcher happened to have running. SINGLE_TOP as well: without
 * it CLEAR_TOP destroys and recreates a MainActivity that is already open,
 * losing the screen the user was on, where with it the running instance
 * receives the action through onNewIntent.
 */
fun openAppIntent(context: Context, action: String): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(action)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

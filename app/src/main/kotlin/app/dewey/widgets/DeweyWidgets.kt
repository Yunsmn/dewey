package app.dewey.widgets

import android.content.Context
import androidx.glance.appwidget.updateAll
import app.dewey.widgets.bills.BillsWidget
import app.dewey.widgets.notes.NotesWidget
import app.dewey.widgets.scan.ScanWidget

/**
 * Pushes fresh content to every home-screen widget Dewey ships.
 *
 * Each widget's own `updatePeriodMillis` fallback is generous (Android also
 * enforces its own minimum around 30 minutes regardless of what a provider
 * asks for), which is fine for a "check back later" tile but not for one the
 * person is looking at right now - a note saved, a bill's fields extracted,
 * or an entitlement bought should show up immediately. Call this wherever
 * that kind of change happens, and whenever the app goes to the background,
 * so a widget never shows something the app itself would consider stale.
 */
suspend fun refreshDeweyWidgets(context: Context) {
    ScanWidget().updateAll(context)
    BillsWidget().updateAll(context)
    NotesWidget().updateAll(context)
}

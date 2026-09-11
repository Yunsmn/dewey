package app.dewey.widgets.notes

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.dewey.R
import app.dewey.widgets.WidgetActions
import app.dewey.widgets.WidgetColors
import app.dewey.widgets.deweyContainer
import app.dewey.widgets.openAppIntent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * "Notes" (about 4x2 cells): pinned notes first, then the most recent, each
 * with a title and a one-line preview. Paid-tier only - see
 * [notesWidgetContent] for the entitlement gate and row selection, kept as
 * pure functions so what to show is unit-testable without a widget host.
 */
class NotesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadContent(context)
        provideContent { NotesWidgetBody(content, context) }
    }

    /**
     * Reads off the main thread, same as every other `provideGlance` in this
     * package, and turns a broken read into the friendly empty state rather
     * than letting the widget host pull a crashing tile - see
     * app.dewey.widgets.bills.BillsWidget for the identical shape.
     */
    private suspend fun loadContent(context: Context): NotesWidgetContent {
        val container = context.deweyContainer()
        return try {
            // Refreshed before it is read, for the reason in BillsWidget: a
            // cold process still holds the entitlement's false default.
            container.entitlements.refresh()
            val isEntitled = container.entitlements.isEntitled.first()
            if (!isEntitled) {
                NotesWidgetContent.Locked
            } else {
                val notes = container.notesRepository.observeNotes().first()
                notesWidgetContent(notes, isEntitled = true)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not load notes for the widget", e)
            NotesWidgetContent.Empty
        }
    }

    private companion object {
        const val TAG = "NotesWidget"
    }
}

@Composable
private fun NotesWidgetBody(content: NotesWidgetContent, context: Context) {
    val intent = openAppIntent(context, WidgetActions.OPEN_NOTES)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(20.dp)
            .clickable(actionStartActivity(intent))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_note),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(WidgetColors.coral),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = "Notes",
                style = TextStyle(color = WidgetColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        when (content) {
            NotesWidgetContent.Locked -> WidgetMessage("Unlock Librarian in Dewey to see your notes here")
            NotesWidgetContent.Empty -> WidgetMessage("No notes yet - jot one down in Dewey")
            is NotesWidgetContent.Rows -> content.notes.forEach { row -> NoteRow(row) }
        }
    }
}

@Composable
private fun NoteRow(row: NoteWidgetRow) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text = row.title,
            style = TextStyle(color = WidgetColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
        Text(
            text = row.preview,
            style = TextStyle(color = WidgetColors.inkMuted, fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun WidgetMessage(text: String) {
    Text(
        text = text,
        style = TextStyle(color = WidgetColors.inkMuted, fontSize = 13.sp),
        maxLines = 3,
    )
}

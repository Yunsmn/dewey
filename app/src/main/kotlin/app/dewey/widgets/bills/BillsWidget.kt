package app.dewey.widgets.bills

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
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * "Bills" (about 4x2 cells): the header count, then the next few bills by due
 * date. Paid-tier only - see [billsWidgetContent] for the entitlement gate and
 * row selection, kept as pure functions so what to show is unit-testable
 * without a widget host.
 */
class BillsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val content = loadContent(context)
        provideContent { BillsWidgetBody(content, context) }
    }

    /**
     * Reads off the main thread, same as every other `provideGlance` in this
     * package, and turns a broken read into the friendly empty state rather
     * than letting the widget host pull a crashing tile - see
     * app.dewey.widgets.notes.NotesWidget for the identical shape.
     */
    private suspend fun loadContent(context: Context): BillsWidgetContent {
        val container = context.deweyContainer()
        return try {
            // Refreshed before it is read. A widget update can start the
            // process cold, when the entitlement still holds its "not yet
            // known" default of false, and a subscriber's widget would show
            // the locked state until the app was next opened.
            container.entitlements.refresh()
            val isEntitled = container.entitlements.isEntitled.first()
            if (!isEntitled) {
                BillsWidgetContent.Locked
            } else {
                val documents = container.documentRepository.observeBills().first()
                billsWidgetContent(documents, isEntitled = true, today = LocalDate.now())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not load bills for the widget", e)
            BillsWidgetContent.Empty
        }
    }

    private companion object {
        const val TAG = "BillsWidget"
    }
}

@Composable
private fun BillsWidgetBody(content: BillsWidgetContent, context: Context) {
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
                provider = ImageProvider(R.drawable.ic_widget_receipt),
                contentDescription = null,
                modifier = GlanceModifier.size(20.dp),
                colorFilter = ColorFilter.tint(WidgetColors.amber),
            )
            Spacer(modifier = GlanceModifier.width(8.dp))
            Text(
                text = headerText(content),
                style = TextStyle(color = WidgetColors.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.height(10.dp))
        when (content) {
            BillsWidgetContent.Locked -> WidgetMessage("Unlock Librarian in Dewey to see your bills here")
            BillsWidgetContent.Empty -> WidgetMessage("No bills due - you're all caught up")
            is BillsWidgetContent.Rows -> content.bills.forEach { row -> BillRow(row) }
        }
    }
}

private fun headerText(content: BillsWidgetContent): String = when (content) {
    is BillsWidgetContent.Rows -> "Bills · ${content.totalCount}"
    else -> "Bills"
}

@Composable
private fun BillRow(row: BillWidgetRow) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
    ) {
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.vendor,
                style = TextStyle(color = WidgetColors.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                text = row.dueWords,
                style = TextStyle(color = WidgetColors.inkMuted, fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(modifier = GlanceModifier.width(8.dp))
        Text(
            text = row.amount,
            style = TextStyle(color = WidgetColors.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium),
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

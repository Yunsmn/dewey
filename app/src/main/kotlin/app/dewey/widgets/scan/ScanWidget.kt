package app.dewey.widgets.scan

import android.content.Context
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.dewey.R
import app.dewey.widgets.WidgetActions
import app.dewey.widgets.WidgetColors
import app.dewey.widgets.openAppIntent

/**
 * The smallest widget Dewey ships (about 2x1 cells): a single tap-target that
 * opens straight into the scanner.
 *
 * Free for everyone - the scanner itself carries no paywall - so unlike
 * [app.dewey.widgets.bills.BillsWidget] and [app.dewey.widgets.notes.NotesWidget]
 * this one never reads an entitlement, and has no "which rows" logic worth
 * pulling into a pure function.
 */
class ScanWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { ScanWidgetBody(context) }
    }
}

@Composable
private fun ScanWidgetBody(context: Context) {
    val intent = openAppIntent(context, WidgetActions.OPEN_SCAN)
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetColors.surface)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity(intent))
            .padding(12.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_scan),
            contentDescription = null,
            modifier = GlanceModifier.size(28.dp),
            colorFilter = ColorFilter.tint(WidgetColors.indigo),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        Text(
            text = "Scan",
            style = TextStyle(color = WidgetColors.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

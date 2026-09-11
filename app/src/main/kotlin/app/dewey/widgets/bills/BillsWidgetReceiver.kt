package app.dewey.widgets.bills

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The system-facing half of [BillsWidget] - see the manifest entry for how it is declared. */
class BillsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BillsWidget()
}

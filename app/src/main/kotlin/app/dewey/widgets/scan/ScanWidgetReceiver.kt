package app.dewey.widgets.scan

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/** The system-facing half of [ScanWidget] - see the manifest entry for how it is declared. */
class ScanWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScanWidget()
}

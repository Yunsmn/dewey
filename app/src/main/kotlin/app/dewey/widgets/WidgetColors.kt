package app.dewey.widgets

import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import app.dewey.ui.theme.DeweyColors

/**
 * Widget colours, day/night, built straight from [DeweyColors]' hex values.
 *
 * Glance cannot read the app's Compose MaterialTheme - GlanceTheme is a
 * separate, unrelated colour system - so this is the one place a widget's
 * palette is defined, using the same pairs the rest of the app uses for
 * ground, ink and each tool's hue (amber for bills, coral for notes, indigo
 * for scan).
 */
object WidgetColors {
    val background: GlanceColorProvider = ColorProvider(day = DeweyColors.GroundLight, night = DeweyColors.Ground)
    val surface: GlanceColorProvider = ColorProvider(day = DeweyColors.SurfaceLight, night = DeweyColors.Surface)
    val ink: GlanceColorProvider = ColorProvider(day = DeweyColors.InkLight, night = DeweyColors.Ink)
    val inkMuted: GlanceColorProvider = ColorProvider(day = DeweyColors.InkMutedLight, night = DeweyColors.InkMuted)
    val amber: GlanceColorProvider = ColorProvider(day = DeweyColors.AmberLight, night = DeweyColors.Amber)
    val coral: GlanceColorProvider = ColorProvider(day = DeweyColors.CoralLight, night = DeweyColors.Coral)
    val indigo: GlanceColorProvider = ColorProvider(day = DeweyColors.IndigoLight, night = DeweyColors.Indigo)
}

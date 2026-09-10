package app.dewey.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * One colour family: [strong] for an icon, a label or a bar, [soft] for the
 * wash behind it. Always used as a pair so a tinted tile never has an icon in
 * some other family's colour.
 */
@Immutable
data class Hue(val strong: Color, val soft: Color)

/**
 * The hues that carry meaning.
 *
 * Tool families each own one, so a lock is always violet and a merge always
 * blue, on the Home grid and on the tool's own screen alike. Categories draw
 * from [categories] by name, so "Insurance" is the same colour on the Home
 * glance, in the Documents chips and on its bills.
 */
@Immutable
data class DeweyHues(
    val scan: Hue,
    val pages: Hue,
    val convert: Hue,
    val protect: Hue,
    val mark: Hue,
    val bills: Hue,
    val assistant: Hue,
    val categories: List<Hue>,
) {
    /**
     * The hue for a category, stable across launches — String.hashCode is
     * specified, not random — and case-insensitive, so "Voiture" and "voiture"
     * never disagree. Two categories can share a hue; with eight to pick from
     * that is rare, and a fixed assignment matters more than a unique one.
     */
    fun forCategory(label: String): Hue =
        categories[Math.floorMod(label.trim().lowercase().hashCode(), categories.size)]
}

/**
 * Semantic colour roles.
 *
 * Screens ask for `paper` and `ink`, never for a hex value or a light/dark
 * branch. Adding a theme then means adding one palette here rather than auditing
 * every composable — which is the difference between a dark mode that works and
 * one that is right in most places.
 */
@Immutable
data class DeweyPalette(
    val paper: Color,
    val paperRaised: Color,
    val paperSunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val rule: Color,
    val accent: Color,
    val accentSoft: Color,
    val attention: Color,
    val attentionSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val onAccent: Color,
    /** The card surface. */
    val glass: Color,
    /** For controls that float over moving content — the navigation bar above all. Always opaque. */
    val glassRaised: Color,
    val glassBorder: Color,
    val hues: DeweyHues,
)

/**
 * Spacing, on a scale rather than ad hoc.
 *
 * Uniform padding everywhere is what makes a layout read as a template. These
 * steps are deliberately uneven at the top so a section break is visibly larger
 * than a row gap.
 */
@Immutable
data class DeweySpacing(
    val hairline: Dp = 2.dp,
    val tight: Dp = 6.dp,
    val row: Dp = 12.dp,
    val gutter: Dp = 20.dp,
    val block: Dp = 32.dp,
    val section: Dp = 52.dp,
)

/**
 * Corner radii. Three steps, not one: a chip, a card and a sheet rounded alike
 * is the uniform-radius look that reads as a template.
 */
@Immutable
data class DeweyRadii(
    val small: Dp = 12.dp,
    val medium: Dp = 20.dp,
    val large: Dp = 28.dp,
)

private fun lightHue(strong: Color) = Hue(strong, strong.copy(alpha = 0.12f))
private fun darkHue(strong: Color) = Hue(strong, strong.copy(alpha = 0.18f))

private val LightHues = DeweyHues(
    scan = lightHue(DeweyColors.IndigoLight),
    pages = lightHue(DeweyColors.SkyLight),
    convert = lightHue(DeweyColors.TealLight),
    protect = lightHue(DeweyColors.VioletLight),
    mark = lightHue(DeweyColors.CoralLight),
    bills = lightHue(DeweyColors.AmberLight),
    assistant = lightHue(DeweyColors.VioletLight),
    categories = listOf(
        DeweyColors.IndigoLight, DeweyColors.TealLight, DeweyColors.CoralLight, DeweyColors.VioletLight,
        DeweyColors.SkyLight, DeweyColors.AmberLight, DeweyColors.RoseLight, DeweyColors.GreenLight,
    ).map(::lightHue),
)

private val DarkHues = DeweyHues(
    scan = darkHue(DeweyColors.Indigo),
    pages = darkHue(DeweyColors.Sky),
    convert = darkHue(DeweyColors.Teal),
    protect = darkHue(DeweyColors.Violet),
    mark = darkHue(DeweyColors.Coral),
    bills = darkHue(DeweyColors.Amber),
    assistant = darkHue(DeweyColors.Violet),
    categories = listOf(
        DeweyColors.Indigo, DeweyColors.Teal, DeweyColors.Coral, DeweyColors.Violet,
        DeweyColors.Sky, DeweyColors.Amber, DeweyColors.Rose, DeweyColors.Green,
    ).map(::darkHue),
)

private val LightPalette = DeweyPalette(
    paper = DeweyColors.GroundLight,
    paperRaised = DeweyColors.SurfaceLight,
    paperSunken = DeweyColors.SurfaceSunkenLight,
    ink = DeweyColors.InkLight,
    inkMuted = DeweyColors.InkMutedLight,
    inkFaint = DeweyColors.InkFaintLight,
    rule = DeweyColors.HairlineLight,
    accent = DeweyColors.AccentLight,
    accentSoft = DeweyColors.AccentSoftLight,
    attention = DeweyColors.AttentionLight,
    attentionSoft = DeweyColors.AttentionSoftLight,
    danger = DeweyColors.DangerLight,
    dangerSoft = DeweyColors.DangerSoftLight,
    onAccent = DeweyColors.OnAccent,
    glass = DeweyColors.SurfaceLight,
    glassRaised = DeweyColors.SurfaceLight,
    glassBorder = DeweyColors.HairlineLight,
    hues = LightHues,
)

private val DarkPalette = DeweyPalette(
    paper = DeweyColors.Ground,
    paperRaised = DeweyColors.Surface,
    paperSunken = DeweyColors.SurfaceSunken,
    ink = DeweyColors.Ink,
    inkMuted = DeweyColors.InkMuted,
    inkFaint = DeweyColors.InkFaint,
    rule = DeweyColors.Hairline,
    accent = DeweyColors.Accent,
    accentSoft = DeweyColors.AccentSoft,
    attention = DeweyColors.Attention,
    attentionSoft = DeweyColors.AttentionSoft,
    danger = DeweyColors.Danger,
    dangerSoft = DeweyColors.DangerSoft,
    onAccent = DeweyColors.OnAccentDark,
    glass = DeweyColors.Surface,
    glassRaised = DeweyColors.Surface,
    glassBorder = DeweyColors.Hairline,
    hues = DarkHues,
)

val LocalDeweyPalette = staticCompositionLocalOf { LightPalette }
val LocalDeweySpacing = staticCompositionLocalOf { DeweySpacing() }
val LocalDeweyRadii = staticCompositionLocalOf { DeweyRadii() }

object Dewey {
    val colors: DeweyPalette
        @Composable get() = LocalDeweyPalette.current

    val spacing: DeweySpacing
        @Composable get() = LocalDeweySpacing.current

    val radii: DeweyRadii
        @Composable get() = LocalDeweyRadii.current

    val type = DeweyType
}

/**
 * @param dark follows the phone's setting. Both palettes are designed rather
 *   than one being an inversion of the other, so neither is the one a new user
 *   should be protected from.
 */
@Composable
fun DeweyTheme(
    dark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkPalette else LightPalette

    // Material components still appear — text fields, the odd dialog — and would
    // otherwise arrive in default purple. Mapping the scheme keeps them in the
    // same world as everything hand-built.
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.paperRaised,
            onSurface = palette.ink,
            surfaceVariant = palette.paperSunken,
            onSurfaceVariant = palette.inkMuted,
            outline = palette.rule,
            error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            background = palette.paper,
            onBackground = palette.ink,
            surface = palette.paperRaised,
            onSurface = palette.ink,
            surfaceVariant = palette.paperSunken,
            onSurfaceVariant = palette.inkMuted,
            outline = palette.rule,
            error = palette.danger,
        )
    }

    CompositionLocalProvider(
        LocalDeweyPalette provides palette,
        LocalDeweySpacing provides DeweySpacing(),
        LocalDeweyRadii provides DeweyRadii(),
    ) {
        MaterialTheme(colorScheme = scheme, typography = DeweyType.material, content = content)
    }
}

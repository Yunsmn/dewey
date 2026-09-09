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
    /** The card surface. Translucent in dark, opaque in light — see the palettes. */
    val glass: Color,
    /**
     * For controls that float over moving content rather than sitting on the
     * ground — the navigation bar above all.
     *
     * Opaque, unlike [glass], and deliberately so. A card is translucent over
     * a background that holds still, and what shows through reads as depth. A
     * bar with a list scrolling underneath it at the same alpha shows filenames
     * sliding through the tab labels: that reads as a rendering fault, not as
     * glass. Compose cannot blur a backdrop below API 31, so depth here comes
     * from a shadow instead.
     */
    val glassRaised: Color,
    val glassBorder: Color,
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
    // Opaque in light. A translucent white card on a near-white ground has
    // nothing to show through it and only costs a blur.
    glass = DeweyColors.SurfaceLight,
    glassRaised = DeweyColors.SurfaceLight,
    glassBorder = DeweyColors.HairlineLight,
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
    onAccent = DeweyColors.OnAccent,
    // The card surface at ~72%, so the ground reads through it. Compose has no
    // backdrop blur below API 31, and blurring a scrolling list would cost more
    // than it returns on a mid-range phone — so the depth here comes from
    // translucency and a lifted hairline rather than from a real blur.
    glass = DeweyColors.Surface.copy(alpha = 0.72f),
    glassRaised = DeweyColors.Surface,
    glassBorder = DeweyColors.Hairline,
)

val LocalDeweyPalette = staticCompositionLocalOf { LightPalette }
val LocalDeweySpacing = staticCompositionLocalOf { DeweySpacing() }

object Dewey {
    val colors: DeweyPalette
        @Composable get() = LocalDeweyPalette.current

    val spacing: DeweySpacing
        @Composable get() = LocalDeweySpacing.current

    val type = DeweyType
}

/**
 * @param dark defaults to true rather than to the system setting. The palette is
 *   designed dark — translucent surfaces over a deep navy ground — and light is
 *   the adaptation, so a phone set to light should not be the first thing a new
 *   user sees the app in. [isSystemInDarkTheme] is still what the setting screen
 *   would hand in once there is one.
 */
@Composable
fun DeweyTheme(
    dark: Boolean = true,
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
    ) {
        MaterialTheme(colorScheme = scheme, typography = DeweyType.material, content = content)
    }
}

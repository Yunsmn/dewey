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
    val onAccent: Color,
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
    paper = DeweyColors.Paper,
    paperRaised = DeweyColors.PaperRaised,
    paperSunken = DeweyColors.PaperSunken,
    ink = DeweyColors.Ink,
    inkMuted = DeweyColors.InkMuted,
    inkFaint = DeweyColors.InkFaint,
    rule = DeweyColors.Rule,
    accent = DeweyColors.Accent,
    accentSoft = DeweyColors.AccentSoft,
    attention = DeweyColors.Attention,
    attentionSoft = DeweyColors.AttentionSoft,
    danger = DeweyColors.Danger,
    onAccent = DeweyColors.Paper,
)

private val DarkPalette = DeweyPalette(
    paper = DeweyColors.PaperDark,
    paperRaised = DeweyColors.PaperRaisedDark,
    paperSunken = DeweyColors.PaperSunkenDark,
    ink = DeweyColors.InkDark,
    inkMuted = DeweyColors.InkMutedDark,
    inkFaint = DeweyColors.InkFaintDark,
    rule = DeweyColors.RuleDark,
    accent = DeweyColors.AccentDark,
    accentSoft = DeweyColors.AccentSoftDark,
    attention = DeweyColors.AttentionDark,
    attentionSoft = DeweyColors.AttentionSoftDark,
    danger = DeweyColors.DangerDark,
    onAccent = DeweyColors.PaperDark,
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
    ) {
        MaterialTheme(colorScheme = scheme, typography = DeweyType.material, content = content)
    }
}

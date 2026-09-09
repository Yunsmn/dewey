package app.dewey.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette: a dark instrument panel with one blue that carries every action.
 *
 * Dark is the default and the design's home. The ground is a deep desaturated
 * navy rather than black — #0B0F17 — so translucent surfaces layered on it read
 * as glass with something behind them instead of as grey rectangles. Black
 * grounds kill that effect entirely, which is why almost no "glassmorphism"
 * survives contact with a pure black background.
 *
 * Blue is the only chromatic accent and it means one thing: you can act on this.
 * Amber and red mean a deadline is near or past. Nothing is coloured for
 * decoration — with three hundred documents on screen, a colour that does not
 * mean anything is noise the reader has to filter.
 */
object DeweyColors {

    // Dark: the default.
    val Ground = Color(0xFF0B0F17)
    /** Sits over [Ground] at ~65% — the glass card. */
    val Surface = Color(0xFF161E2E)
    val SurfaceSunken = Color(0xFF080B11)
    val Ink = Color(0xFFF1F5F9)
    val InkMuted = Color(0xFF94A3B8)
    val InkFaint = Color(0xFF64748B)
    /** Hairlines are white at low alpha, not a grey: they must lift off glass. */
    val Hairline = Color(0x14FFFFFF)
    val Accent = Color(0xFF3B82F6)
    val AccentPressed = Color(0xFF2563EB)
    /** The wash behind an accent chip. Blue at low alpha, so glass shows through. */
    val AccentSoft = Color(0x1A3B82F6)
    val Attention = Color(0xFFF59E0B)
    val AttentionSoft = Color(0x26F59E0B)
    val Danger = Color(0xFFEF4444)
    val DangerSoft = Color(0x26EF4444)
    val OnAccent = Color(0xFFFFFFFF)

    // Light: the same instrument in daylight, not an inverted document. The
    // ground is a cool near-white so the blue stays the brightest thing on it.
    val GroundLight = Color(0xFFF1F5F9)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceSunkenLight = Color(0xFFE2E8F0)
    val InkLight = Color(0xFF0F172A)
    val InkMutedLight = Color(0xFF64748B)
    val InkFaintLight = Color(0xFF94A3B8)
    val HairlineLight = Color(0x0F000000)
    val AccentLight = Color(0xFF2563EB)
    val AccentPressedLight = Color(0xFF1D4ED8)
    val AccentSoftLight = Color(0x142563EB)
    val AttentionLight = Color(0xFFB45309)
    val AttentionSoftLight = Color(0x1FB45309)
    val DangerLight = Color(0xFFDC2626)
    val DangerSoftLight = Color(0x1FDC2626)
}

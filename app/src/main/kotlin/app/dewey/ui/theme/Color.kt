package app.dewey.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette: quiet grounds, and colour that means something on top of them.
 *
 * The neutrals are cool and soft so the colour placed on them can be generous
 * without the screen turning loud. Colour then carries meaning a reader takes
 * in before reading a label: each family of tools has its own hue, a category
 * keeps one hue everywhere it appears, and amber and red still mean a deadline
 * is near or past.
 *
 * Every hue exists twice. The light value is deep enough to hold contrast on
 * white; the dark value is lifted so it does not sink into the navy ground.
 */
object DeweyColors {

    // Dark.
    val Ground = Color(0xFF0F1320)
    val Surface = Color(0xFF1A2033)
    val SurfaceSunken = Color(0xFF0A0D17)
    val Ink = Color(0xFFEEF1F8)
    val InkMuted = Color(0xFFA2ABBE)
    val InkFaint = Color(0xFF6C758A)
    val Hairline = Color(0x1AFFFFFF)
    val Accent = Color(0xFF8A9DFF)
    val AccentPressed = Color(0xFF7488F5)
    val AccentSoft = Color(0x2E8A9DFF)
    val Attention = Color(0xFFF5BE5B)
    val AttentionSoft = Color(0x2EF5BE5B)
    val Danger = Color(0xFFFF7A70)
    val DangerSoft = Color(0x2EFF7A70)
    /** Dark ink on the lifted dark accent: white on it would not hold contrast. */
    val OnAccentDark = Color(0xFF0F1320)

    // Light.
    val GroundLight = Color(0xFFF5F6FA)
    val SurfaceLight = Color(0xFFFFFFFF)
    val SurfaceSunkenLight = Color(0xFFECEEF4)
    val InkLight = Color(0xFF1B2030)
    val InkMutedLight = Color(0xFF5E6678)
    val InkFaintLight = Color(0xFF9AA2B3)
    val HairlineLight = Color(0x141B2030)
    val AccentLight = Color(0xFF4F6BED)
    val AccentPressedLight = Color(0xFF3D57D6)
    val AccentSoftLight = Color(0x1F4F6BED)
    val AttentionLight = Color(0xFFC77A12)
    val AttentionSoftLight = Color(0x1FC77A12)
    val DangerLight = Color(0xFFD9443C)
    val DangerSoftLight = Color(0x1FD9443C)
    val OnAccent = Color(0xFFFFFFFF)

    // Hues, light then dark.
    val IndigoLight = Color(0xFF4F6BED)
    val Indigo = Color(0xFF8A9DFF)
    val SkyLight = Color(0xFF2B86E0)
    val Sky = Color(0xFF63AEFF)
    val TealLight = Color(0xFF0E9C84)
    val Teal = Color(0xFF45D1B4)
    val VioletLight = Color(0xFF8457E8)
    val Violet = Color(0xFFB097FF)
    val CoralLight = Color(0xFFE5674A)
    val Coral = Color(0xFFFF9B82)
    val AmberLight = Color(0xFFC98512)
    val Amber = Color(0xFFF5BE5B)
    val RoseLight = Color(0xFFD9467A)
    val Rose = Color(0xFFFF8DB5)
    val GreenLight = Color(0xFF3A9A4A)
    val Green = Color(0xFF7ED68A)
}

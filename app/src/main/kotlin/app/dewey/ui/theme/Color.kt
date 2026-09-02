package app.dewey.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette, as an archive rather than an app.
 *
 * Warm paper instead of white, warm near-black instead of grey, one deep green
 * that carries every action. Colour is used semantically — the accent means "you
 * can act on this" and the amber means "this needs your attention" — never as
 * decoration. That restraint is what keeps a list of three hundred documents
 * readable.
 */
object DeweyColors {

    // Light: the default, and the one the product is designed around.
    val Paper = Color(0xFFFAF7F2)
    val PaperRaised = Color(0xFFFFFDFA)
    val PaperSunken = Color(0xFFF2EDE4)
    val Ink = Color(0xFF1A1815)
    val InkMuted = Color(0xFF6B6560)
    val InkFaint = Color(0xFF9A938B)
    val Rule = Color(0xFFE3DDD4)
    val Accent = Color(0xFF1B4332)
    val AccentSoft = Color(0xFFE8F0EA)
    val Attention = Color(0xFF8A5A00)
    val AttentionSoft = Color(0xFFFBF0DC)
    val Danger = Color(0xFF8C2F1F)

    // Dark: a dimmed reading room, not an inverted document. The ground stays
    // warm and the accent lifts, because #1B4332 on near-black is unreadable.
    val PaperDark = Color(0xFF15130F)
    val PaperRaisedDark = Color(0xFF1F1C17)
    val PaperSunkenDark = Color(0xFF0F0D0A)
    val InkDark = Color(0xFFF0EBE2)
    val InkMutedDark = Color(0xFFA79F94)
    val InkFaintDark = Color(0xFF6F6759)
    val RuleDark = Color(0xFF322D25)
    val AccentDark = Color(0xFF7FBF9A)
    val AccentSoftDark = Color(0xFF1B2A22)
    val AttentionDark = Color(0xFFE0A93C)
    val AttentionSoftDark = Color(0xFF2A2113)
    val DangerDark = Color(0xFFD9705C)
}

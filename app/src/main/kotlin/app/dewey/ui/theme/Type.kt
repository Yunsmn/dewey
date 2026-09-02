package app.dewey.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * A real pairing: serif for what you read, sans for what you scan.
 *
 * Headings and document titles are serif, because they are the content. Metadata
 * — sizes, counts, filenames, amounts — is sans, because it is scanned rather
 * than read. The contrast between the two is what gives the list hierarchy
 * without needing boxes or colour to create it.
 *
 * Both families are on every Android device, so this costs no download, no font
 * loading state and no layout shift.
 */
object DeweyType {

    private val Serif = FontFamily.Serif
    private val Sans = FontFamily.SansSerif

    /** Screen titles. Large enough to be the anchor of the page. */
    val Display = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.5).sp,
    )

    /** Section headings, and the title of a document. */
    val Title = TextStyle(
        fontFamily = Serif,
        fontWeight = FontWeight.Medium,
        fontSize = 19.sp,
        lineHeight = 25.sp,
    )

    /** Body copy: summaries, snippets, explanations. */
    val Body = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** Metadata beneath a title. */
    val Meta = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /**
     * Section rules and category labels. Letterspaced small caps — the one
     * typographic flourish, and what makes the screen read as a catalogue.
     */
    val Label = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.2.sp,
    )

    /**
     * Filenames and amounts. Monospaced so digits align down a column and
     * `Scan_20240312_004.pdf` is legible as the machine string it is.
     */
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val Button = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )

    /** Material's own components read from this; ours read the styles above. */
    val material = Typography(
        displayLarge = Display,
        headlineMedium = Title,
        titleMedium = Title,
        bodyLarge = Body,
        bodyMedium = Body,
        bodySmall = Meta,
        labelSmall = Label,
        labelLarge = Button,
    )
}

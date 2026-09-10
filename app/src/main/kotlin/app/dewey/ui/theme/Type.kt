package app.dewey.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * One family, held together by weight rather than by mixing a serif in.
 *
 * The catalogue voice this app started with — serif titles against sans
 * metadata — read as quiet and a little cold once the rest of the interface
 * turned soft and colourful with icon tiles and washes of hue. A single
 * friendly sans, semibold where something needs to anchor the eye and regular
 * where it's read in passing, matches that warmth without needing a second
 * typeface to justify itself. Hierarchy now comes from size and weight,
 * the same way the icon tiles carry meaning through colour rather than shape.
 *
 * Every style is on the system font, so this still costs no download, no
 * loading state and no layout shift.
 */
object DeweyType {

    private val Sans = FontFamily.SansSerif

    /** Screen titles. Large enough to be the anchor of the page. */
    val Display = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 30.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.3).sp,
    )

    /**
     * Between [Display] and [Title]: a tool screen's own heading beside its
     * large icon tile, where [Display] would crowd the tile and [Title] would
     * read as a mere section break rather than the page's subject.
     */
    val Headline = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.2).sp,
    )

    /** Section headings, and the title of a document. */
    val Title = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
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
     * A field or section label, set in sentence case rather than shouted in
     * small caps. Weight carries the emphasis a wide letter-spaced label used
     * to carry, so a form still reads as organised without reading as a filing
     * cabinet.
     */
    val Label = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
    )

    /**
     * What a bill costs.
     *
     * Monospaced like [Mono] so amounts align down a column, but set at reading
     * size and heavy — on the bills screen the number is the answer, and it
     * should be the first thing found rather than something read off the end of
     * a row.
     */
    val Amount = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    )

    /**
     * Tab labels and chips. Smaller than [Label] and without its weight, so a
     * bottom nav label sits quietly under its icon.
     */
    val Micro = TextStyle(
        fontFamily = Sans,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
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
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        textAlign = TextAlign.Center,
    )

    /** Material's own components read from this; ours read the styles above. */
    val material = Typography(
        displayLarge = Display,
        headlineMedium = Headline,
        titleMedium = Title,
        bodyLarge = Body,
        bodyMedium = Body,
        bodySmall = Meta,
        labelSmall = Label,
        labelLarge = Button,
    )
}

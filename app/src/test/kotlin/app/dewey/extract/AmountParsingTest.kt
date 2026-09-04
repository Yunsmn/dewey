package app.dewey.extract

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AmountParsingTest {

    @Test
    fun `parses a plain decimal`() {
        assertThat(AmountParsing.parse("281.26")).isEqualTo(281.26)
    }

    @Test
    fun `parses a plain integer`() {
        assertThat(AmountParsing.parse("5000")).isEqualTo(5000.0)
    }

    @Test
    fun `parses a French-grouped amount with a space and a comma decimal`() {
        // The task's own example of the ambiguity: 1 234,56.
        assertThat(AmountParsing.parse("1 234,56")).isEqualTo(1234.56)
    }

    @Test
    fun `parses a French-grouped amount with a non-breaking space`() {
        assertThat(AmountParsing.parse("1 234,56")).isEqualTo(1234.56)
    }

    @Test
    fun `parses a French-grouped amount with a narrow non-breaking space`() {
        assertThat(AmountParsing.parse("1 234,56")).isEqualTo(1234.56)
    }

    @Test
    fun `parses an English-grouped amount with a comma and a dot decimal`() {
        assertThat(AmountParsing.parse("1,234.56")).isEqualTo(1234.56)
    }

    @Test
    fun `parses a European-grouped amount with a dot and a comma decimal`() {
        assertThat(AmountParsing.parse("1.234,56")).isEqualTo(1234.56)
    }

    @Test
    fun `parses a bare comma as a decimal mark`() {
        assertThat(AmountParsing.parse("281,26")).isEqualTo(281.26)
    }

    @Test
    fun `parses a large ungrouped amount`() {
        assertThat(AmountParsing.parse("15023.26")).isEqualTo(15023.26)
    }

    @Test
    fun `parses a negative amount`() {
        assertThat(AmountParsing.parse("-56.43")).isEqualTo(-56.43)
    }

    @Test
    fun `parses multiple grouping separators`() {
        assertThat(AmountParsing.parse("12 345 678,90")).isEqualTo(12345678.90)
    }

    @Test
    fun `trims surrounding whitespace`() {
        assertThat(AmountParsing.parse("  281.26  ")).isEqualTo(281.26)
    }

    @Test
    fun `returns null for an empty string`() {
        assertThat(AmountParsing.parse("")).isNull()
    }

    @Test
    fun `returns null for a lone minus sign`() {
        assertThat(AmountParsing.parse("-")).isNull()
    }

    @Test
    fun `returns null for non-numeric text`() {
        assertThat(AmountParsing.parse("abc")).isNull()
    }

    @Test
    fun `returns null rather than throw for a malformed multi-dot number`() {
        assertThat(AmountParsing.parse("12.34.56")).isNull()
    }

    @Test
    fun `survives hostile input without throwing`() {
        val nasty = listOf(
            "", "-", "--", "....", ",,,,", ".", ",", " ", " ",
            "1".repeat(5_000), "-".repeat(50), "1,2,3,4,5,6",
        )
        for (text in nasty) {
            AmountParsing.parse(text) // must not throw
        }
    }
}

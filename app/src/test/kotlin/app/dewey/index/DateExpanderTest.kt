package app.dewey.index

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DateExpanderTest {

    private val augustAr = "غشت"
    private val novemberAr = "نونبر"
    private val levantineAugust = "أغسطس"

    @Test
    fun `expands an ISO date into three languages`() {
        val expanded = DateExpander.expansions("Emise le 2023-08-14 a Casablanca")

        assertThat(expanded).containsAtLeast("aout 2023", "August 2023", "$augustAr 2023")
    }

    @Test
    fun `reads slash dates day first`() {
        // Moroccan convention: 03/04/2024 is 3 April, not 4 March.
        val expanded = DateExpander.expansions("Echeance 03/04/2024")

        assertThat(expanded).contains("avril 2024")
        assertThat(expanded).doesNotContain("mars 2024")
    }

    @Test
    fun `reads dotted dates`() {
        assertThat(DateExpander.expansions("15.11.2022")).contains("novembre 2022")
    }

    @Test
    fun `uses Moroccan month names not Levantine ones`() {
        val expanded = DateExpander.expansions("2024-08-01")

        assertThat(expanded).contains("$augustAr 2024")
        assertThat(expanded).doesNotContain("$levantineAugust 2024")
    }

    @Test
    fun `expands Arabic November correctly`() {
        assertThat(DateExpander.expansions("2024-11-05")).contains("$novemberAr 2024")
    }

    @Test
    fun `ignores impossible months`() {
        assertThat(DateExpander.expansions("2023-13-01")).isEmpty()
        assertThat(DateExpander.expansions("2023-00-01")).isEmpty()
    }

    @Test
    fun `does not duplicate repeated months`() {
        val expanded = DateExpander.expansions("2023-08-14 and again 2023-08-30")

        assertThat(expanded).hasSize(3)
    }

    @Test
    fun `returns nothing when there are no dates`() {
        assertThat(DateExpander.expansions("Facture Lydec sans date")).isEmpty()
    }

    @Test
    fun `augment leaves dateless text untouched`() {
        val text = "Facture Lydec"

        assertThat(DateExpander.augment(text)).isEqualTo(text)
    }

    @Test
    fun `augment keeps the original text intact`() {
        val text = "Facture du 2023-08-14"

        val augmented = DateExpander.augment(text)

        assertThat(augmented).startsWith(text)
        assertThat(augmented).contains("aout 2023")
    }

    @Test
    fun `is bounded on text full of dates`() {
        val many = (1..500).joinToString(" ") { "2023-%02d-01".format((it % 12) + 1) }

        val expanded = DateExpander.expansions(many)

        assertThat(expanded.size).isAtMost(45)
    }

    @Test
    fun `survives malformed and hostile input`() {
        val nasty = listOf(
            "", "----", "9999-99-99", "0000-00-00", "//////", "....",
            "2023-8-1", "1/1/2023", " - - ", "20230814", "2023--08--14",
            "\u0000", "a".repeat(10_000),
        )
        for (text in nasty) {
            DateExpander.augment(text)
        }
    }
}

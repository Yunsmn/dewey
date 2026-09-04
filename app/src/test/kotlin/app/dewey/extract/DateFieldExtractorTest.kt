package app.dewey.extract

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

/**
 * Mirrors template shapes from tools/corpus/templates.py - see the class doc
 * on AmountExtractorTest for why that matters.
 */
class DateFieldExtractorTest {

    @Test
    fun `finds only a due date on a utility bill, no issue date`() {
        // Lydec never states when the bill was issued - only when it is due.
        // See extract_bench.py's module docstring for why the ground truth
        // "date" field is not comparable to this at all for this category.
        val text = """
            FACTURE DE CONSOMMATION - JANVIER 2023
            Total a payer : 281.26 MAD
            Date limite de paiement : 2023-01-28
        """.trimIndent()

        val dates = DateFieldExtractor.extract(text)

        assertThat(dates.issueDate).isNull()
        assertThat(dates.dueDate).isEqualTo(LocalDate.of(2023, 1, 28))
    }

    @Test
    fun `finds both dates on an insurance policy under different labels`() {
        val text = """
            Date d'effet : 2023-03-01
            Date d'echeance : 2024-03-01
            Prime annuelle : 4200 MAD
        """.trimIndent()

        val dates = DateFieldExtractor.extract(text)

        assertThat(dates.issueDate).isEqualTo(LocalDate.of(2023, 3, 1))
        assertThat(dates.dueDate).isEqualTo(LocalDate.of(2024, 3, 1))
    }

    @Test
    fun `reads the issue date from a rental contract's fait a line`() {
        val text = """
            CONTRAT DE BAIL A USAGE D'HABITATION
            Fait a Casablanca, le 2021-11-01
            ENTRE LES SOUSSIGNES
        """.trimIndent()

        assertThat(DateFieldExtractor.extract(text).issueDate).isEqualTo(LocalDate.of(2021, 11, 1))
    }

    @Test
    fun `reads the bank statement's closing date, not the period start`() {
        val text = """
            Periode du 2024-01-01 au 2024-01-28
            Solde initial : 4955.47 MAD
            2024-01-01 Remise de cheque 2953.41 MAD solde 7908.88 MAD
            Solde final au 2024-01-28 : 13310.34 MAD
        """.trimIndent()

        // "Solde final" is a stronger, explicit label than the generic
        // "Periode : du X au Y" fallback, and both are present here.
        assertThat(DateFieldExtractor.extract(text).issueDate).isEqualTo(LocalDate.of(2024, 1, 28))
    }

    @Test
    fun `falls back to a date range's start when nothing else is labelled`() {
        // The one template shape in the test corpus with no date label at
        // all: an internship's only date is "Periode : du X au Y".
        val text = """
            CONVENTION DE STAGE
            Periode : du 2023-04-01 au 2023-09-28
            Gratification mensuelle : 3000 MAD
        """.trimIndent()

        assertThat(DateFieldExtractor.extract(text).issueDate).isEqualTo(LocalDate.of(2023, 4, 1))
    }

    @Test
    fun `finds the due date when the Arabic label and value are on separate lines`() {
        val text = """
            آخر أجل للأداء :
            2023-01-27
        """.trimIndent()

        assertThat(DateFieldExtractor.extract(text).dueDate).isEqualTo(LocalDate.of(2023, 1, 27))
    }

    @Test
    fun `finds the issue date from an Arabic consultation label on the next line`() {
        val text = """
            تاريخ الفحص :
            2023-11-14
        """.trimIndent()

        assertThat(DateFieldExtractor.extract(text).issueDate).isEqualTo(LocalDate.of(2023, 11, 14))
    }

    @Test
    fun `reports no dates for text with no date label and no range phrase`() {
        val text = "Universite Mohammed VI Polytechnique\nOFFICIAL ACADEMIC TRANSCRIPT\nGrade point average: 15.2"

        val dates = DateFieldExtractor.extract(text)

        assertThat(dates.issueDate).isNull()
        assertThat(dates.dueDate).isNull()
    }

    @Test
    fun `does not treat a due-labelled line as also an issue date`() {
        val text = "Date limite de paiement : 2023-01-28"

        val dates = DateFieldExtractor.extract(text)

        assertThat(dates.dueDate).isEqualTo(LocalDate.of(2023, 1, 28))
        assertThat(dates.issueDate).isNull()
    }

    @Test
    fun `takes the first matching label when more than one line qualifies`() {
        val text = """
            Date d'achat : 2023-01-05
            Fait a Casablanca, le 2023-01-06
        """.trimIndent()

        assertThat(DateFieldExtractor.extract(text).issueDate).isEqualTo(LocalDate.of(2023, 1, 5))
    }

    @Test
    fun `survives hostile input without throwing`() {
        val nasty = listOf(
            "", "\n".repeat(1_000), "Date limite de paiement :".repeat(500),
            "a".repeat(100_000), "2023-13-45 date d'echeance", " ",
        )
        for (text in nasty) {
            DateFieldExtractor.extract(text) // must not throw
        }
    }
}

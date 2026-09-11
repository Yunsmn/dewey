package app.dewey.ui.library

import app.dewey.domain.model.DocType

/**
 * The plural a category of documents is shown under.
 *
 * Moved here from the old library screen when Documents and Home replaced it:
 * the screen went away, but Home's category glance, the Documents chips and
 * list, and the library's own section grouping all still name categories with
 * these words, and they must agree.
 */
internal fun DocType.readable(): String = when (this) {
    DocType.UNKNOWN -> "Unsorted"
    DocType.UTILITY_BILL -> "Bills"
    DocType.BANK_STATEMENT -> "Bank"
    DocType.INVOICE -> "Invoices"
    DocType.RENTAL_CONTRACT -> "Contracts"
    DocType.MEDICAL -> "Medical"
    DocType.UNIVERSITY -> "University"
    DocType.INSURANCE -> "Insurance"
    DocType.EMPLOYMENT -> "Employment"
    DocType.TAX -> "Tax"
    DocType.WARRANTY -> "Warranties"
    DocType.ADMIN -> "Admin"
    DocType.TRAVEL -> "Travel"
    DocType.PAPER -> "Papers"
}

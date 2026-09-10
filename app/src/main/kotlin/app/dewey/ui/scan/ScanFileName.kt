package app.dewey.ui.scan

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The suggested name for a scan captured at [capturedAt], e.g.
 * "Scan 2026-09-10 14.32.pdf".
 *
 * Pure and takes the moment as a parameter rather than calling
 * `LocalDateTime.now()` itself, so a test can hand it a fixed instant instead
 * of racing the clock — the same reasoning as [app.dewey.ui.bills.BillsUiState.today].
 */
fun scanFileName(capturedAt: LocalDateTime): String =
    "Scan ${capturedAt.format(SCAN_NAME_PATTERN)}.pdf"

private val SCAN_NAME_PATTERN: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm")

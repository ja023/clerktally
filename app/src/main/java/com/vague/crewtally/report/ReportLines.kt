package com.vague.crewtally.report

import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.balance.OwedStatus
import com.vague.crewtally.util.CurrencyCodes
import com.vague.crewtally.util.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns a [ClerkStatement] or [CompanyTotals] into a flat list of short lines — the ONE shared
 * source of report content both renderers draw from (LOCKED Phase 5: "two renderers over the
 * same model"). [com.vague.crewtally.report.TextReportRenderer] joins these with newlines for a
 * WhatsApp-clean message; the PDF renderer draws each line and paginates them via
 * [ReportPaginator]. No line here runs wider than ~40 characters by design (short clerk names
 * aside), matching the WhatsApp-clean spirit LOCKED for the text report.
 */
object ReportLines {

    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ROOT)

    fun forClerkStatement(statement: ClerkStatement, strings: ReportStrings): List<String> {
        val symbol = symbolFor(statement.currency)
        val lines = mutableListOf<String>()

        lines += "${strings.appName} - ${strings.clerkStatementTitle}"
        lines += "${statement.clerkName} - ${statement.companyName} - ${statement.projectName}"
        lines += "${statement.currency} | ${dateRangeText(statement.rangeStart, statement.rangeEnd)}"
        lines += ""
        lines += summaryLine(statement, strings, symbol)
        lines += ""

        lines += "${strings.daysWorkedLabel} (${statement.presentDayCount})"
        if (statement.dayLines.isEmpty()) {
            lines += " ${strings.noneRecordedLabel}"
        } else {
            statement.dayLines.forEach { day ->
                val state = if (day.present) strings.presentLabel else strings.absentLabel
                lines += " ${DATE_FORMAT.format(day.date)}  $state  ${Money.formatWithSymbol(day.rateSnapshot, symbol)}"
            }
        }
        lines += ""

        lines += strings.extrasLabel
        if (statement.extraLines.isEmpty()) {
            lines += " ${strings.noneRecordedLabel}"
        } else {
            statement.extraLines.forEach { extra ->
                val amountText = if (extra.amount < 0) {
                    "${strings.deductionLabel} ${Money.formatSignedWithSymbol(extra.amount, symbol)}"
                } else {
                    Money.formatWithSymbol(extra.amount, symbol)
                }
                lines += " ${DATE_FORMAT.format(extra.date)}  ${extra.label}  $amountText"
            }
            lines += " ${strings.extrasLabel}: ${Money.formatSignedWithSymbol(statement.extras, symbol)}"
        }
        lines += ""

        lines += strings.paymentsLabel
        if (statement.paymentLines.isEmpty()) {
            lines += " ${strings.noneRecordedLabel}"
        } else {
            statement.paymentLines.forEach { payment ->
                val notePart = payment.note.ifBlank { null }
                lines += " ${DATE_FORMAT.format(payment.date)}  ${Money.formatWithSymbol(payment.amount, symbol)}" +
                    (notePart?.let { "  $it" } ?: "")
            }
        }
        lines += ""

        lines += "${strings.earnedLabel}: ${Money.formatSignedWithSymbol(statement.earned + statement.extras, symbol)}"
        lines += "${strings.paidLabel}: ${Money.formatWithSymbol(statement.paid, symbol)}"
        lines += "${strings.owedLabel}: ${owedPhrase(statement.owed, symbol, strings)}"

        return lines
    }

    fun forCompanyTotals(totals: CompanyTotals, strings: ReportStrings): List<String> {
        val lines = mutableListOf<String>()

        lines += "${strings.appName} - ${strings.companyReportTitle}"
        lines += totals.companyName
        lines += rangeText(totals.range, strings)
        lines += ""

        if (totals.projectSections.isEmpty()) {
            lines += strings.noneRecordedLabel
        } else {
            totals.projectSections.forEach { section ->
                val symbol = symbolFor(section.currency)
                lines += "${strings.projectLabel}: ${section.projectName} (${section.currency})"
                if (section.clerkRows.isEmpty()) {
                    lines += " ${strings.noneRecordedLabel}"
                } else {
                    section.clerkRows.forEach { row ->
                        lines += " ${row.clerkName}: ${strings.earnedLabel.lowercase(Locale.ROOT)} " +
                            "${Money.formatSignedWithSymbol(row.earned, symbol)}, ${strings.paidLabel.lowercase(Locale.ROOT)} " +
                            "${Money.formatWithSymbol(row.paid, symbol)}, ${owedPhrase(row.owed, symbol, strings)}"
                    }
                }
                if (section.activityLines.isNotEmpty()) {
                    lines += ""
                    section.activityLines.forEach { line -> lines += " ${activityLineText(line, symbol, strings)}" }
                }
                lines += ""
                lines += " ${strings.earnedLabel} ${Money.formatSignedWithSymbol(section.projectEarned, symbol)}, " +
                    "${strings.paidLabel} ${Money.formatWithSymbol(section.projectPaid, symbol)}, " +
                    owedPhrase(section.projectOwed, symbol, strings)
                lines += ""
            }
        }

        lines += strings.grandTotalLabel
        if (totals.grandTotalsByCurrency.isEmpty()) {
            lines += " ${strings.noneRecordedLabel}"
        } else {
            totals.grandTotalsByCurrency.forEach { total ->
                val symbol = symbolFor(total.currency)
                lines += " ${total.currency}: ${strings.earnedLabel.lowercase(Locale.ROOT)} " +
                    "${Money.formatSignedWithSymbol(total.earned, symbol)}, ${strings.paidLabel.lowercase(Locale.ROOT)} " +
                    "${Money.formatWithSymbol(total.paid, symbol)}, ${owedPhrase(total.owed, symbol, strings)}"
            }
        }

        return lines
    }

    /**
     * The project statement (NEW v1.1 report): a header, then a literal LOCKED column header
     * ("CLERK / DAYS / EARNED / PAID / OWED") over one row per clerk, then a PROJECT TOTAL line.
     */
    fun forProjectStatement(statement: ProjectStatement, strings: ReportStrings): List<String> {
        val symbol = symbolFor(statement.currency)
        val lines = mutableListOf<String>()

        lines += "${strings.appName} - ${strings.projectStatementTitle}"
        lines += "${statement.projectName} - ${statement.companyName}"
        lines += "${statement.currency} | ${rangeText(statement.range, strings)}"
        lines += ""

        lines += strings.projectStatementHeaderLabel
        if (statement.clerkRows.isEmpty()) {
            lines += " ${strings.noneRecordedLabel}"
        } else {
            statement.clerkRows.forEach { row ->
                lines += " ${row.clerkName}  ${row.daysWorked}  ${Money.formatSignedWithSymbol(row.earned, symbol)}  " +
                    "${Money.formatWithSymbol(row.paid, symbol)}  ${owedPhrase(row.owed, symbol, strings)}"
            }
        }
        lines += ""

        lines += strings.projectTotalLabel
        lines += " ${strings.earnedLabel}: ${Money.formatSignedWithSymbol(statement.totalEarned, symbol)}"
        lines += " ${strings.paidLabel}: ${Money.formatWithSymbol(statement.totalPaid, symbol)}"
        lines += " ${strings.owedLabel}: ${owedPhrase(statement.totalOwed, symbol, strings)}"

        return lines
    }

    /** Renders one [CompanyActivityLine], matching the LOCKED v1.1 examples ("Mar 3  Ali Hassan - day  $25", "Payment -> clerk  -$100"). */
    private fun activityLineText(line: CompanyActivityLine, symbol: String, strings: ReportStrings): String {
        val datePart = DATE_FORMAT.format(line.date)
        return when (line) {
            is CompanyDayActivityLine ->
                "$datePart  ${line.clerkName} - ${strings.dayActivityLabel}  ${Money.formatWithSymbol(line.amount, symbol)}"
            is CompanyExtraActivityLine ->
                "$datePart  ${line.clerkName} - ${line.label}  ${Money.formatSignedWithSymbol(line.amount, symbol)}"
            is CompanyPaymentActivityLine -> {
                val notePart = line.note.ifBlank { null }
                "$datePart  ${String.format(Locale.ROOT, strings.paymentArrowTemplate, line.clerkName)}  " +
                    Money.formatSignedWithSymbol(line.amount, symbol) + (notePart?.let { "  $it" } ?: "")
            }
        }
    }

    /** The v1.1 header's range wording: [ReportStrings.allTimeLabel] or a formatted from-to span. */
    private fun rangeText(range: ReportDateRange, strings: ReportStrings): String {
        val start = range.start
        val end = range.end
        return when {
            start == null && end == null -> strings.allTimeLabel
            start != null && end != null -> "${DATE_FORMAT.format(start)} - ${DATE_FORMAT.format(end)}"
            start != null -> DATE_FORMAT.format(start)
            else -> DATE_FORMAT.format(requireNotNull(end))
        }
    }

    /**
     * "N days x RATE" only tells the truth when every present day shares one rate. A mid-project
     * rate edit (LOCKED #2/roster: future days only, past days keep their rateSnapshot) means two
     * present days can carry different rates, and multiplying the day count by any single one of
     * them would misstate the total — so that case drops the multiplier and states the earned
     * total directly instead ([ReportStrings.summaryLineVaryingRateTemplate]).
     */
    private fun summaryLine(statement: ClerkStatement, strings: ReportStrings, symbol: String): String {
        val presentRates = statement.dayLines.filter { it.present }.map { it.rateSnapshot }.distinct()

        return if (presentRates.size <= 1) {
            String.format(
                Locale.ROOT,
                strings.summaryLineTemplate,
                statement.clerkName,
                statement.presentDayCount,
                Money.formatWithSymbol(presentRates.firstOrNull() ?: 0L, symbol),
                Money.formatSignedWithSymbol(statement.extras, symbol),
                Money.formatWithSymbol(statement.paid, symbol),
                owedPhrase(statement.owed, symbol, strings),
            )
        } else {
            String.format(
                Locale.ROOT,
                strings.summaryLineVaryingRateTemplate,
                statement.clerkName,
                statement.presentDayCount,
                Money.formatWithSymbol(statement.earned, symbol),
                Money.formatSignedWithSymbol(statement.extras, symbol),
                Money.formatWithSymbol(statement.paid, symbol),
                owedPhrase(statement.owed, symbol, strings),
            )
        }
    }

    private fun owedPhrase(owed: Long, symbol: String, strings: ReportStrings): String =
        when (BalanceCalculator.statusOf(owed)) {
            OwedStatus.OWED -> String.format(Locale.ROOT, strings.owedPhraseOwedTemplate, Money.formatWithSymbol(owed, symbol))
            OwedStatus.ADVANCE ->
                String.format(Locale.ROOT, strings.owedPhraseAdvanceTemplate, Money.formatWithSymbol(-owed, symbol))
            OwedStatus.SETTLED -> strings.owedPhraseSettled
        }

    private fun dateRangeText(start: LocalDate?, end: LocalDate?): String = when {
        start == null || end == null -> ""
        start == end -> DATE_FORMAT.format(start)
        else -> "${DATE_FORMAT.format(start)} - ${DATE_FORMAT.format(end)}"
    }

    private fun symbolFor(code: String): String = CurrencyCodes.symbolFor(code)
}

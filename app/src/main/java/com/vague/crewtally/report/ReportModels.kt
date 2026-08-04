package com.vague.crewtally.report

import java.time.LocalDate

/**
 * One attendance day on a clerk statement: the date, whether the clerk was present, and the
 * rate snapshot that day carried. Absent days still appear (LOCKED Phase 5 — day lines list
 * date/present/rate) so the statement reads as a full record, not just the paid days.
 */
data class ClerkStatementDayLine(val date: LocalDate, val present: Boolean, val rateSnapshot: Long)

/** One signed extra-pay line on a clerk statement (negative = deduction). */
data class ClerkStatementExtraLine(val date: LocalDate, val label: String, val amount: Long)

/** One payment line on a clerk statement. */
data class ClerkStatementPaymentLine(val date: LocalDate, val amount: Long, val note: String)

/**
 * The full per-clerk, per-project statement (LOCKED Phase 5 report #1): header identity, every
 * day/extra/payment line, and the same totals [com.vague.crewtally.balance.BalanceCalculator]
 * would derive for the balance screen — so a statement and the balance screen never disagree.
 * [rangeStart]/[rangeEnd] are null when the clerk has no activity at all on this project (the
 * empty-clerk edge case); every other list is then also empty and every total is zero.
 */
data class ClerkStatement(
    val clerkName: String,
    val projectName: String,
    val companyName: String,
    val currency: String,
    val rangeStart: LocalDate?,
    val rangeEnd: LocalDate?,
    val dayLines: List<ClerkStatementDayLine>,
    val extraLines: List<ClerkStatementExtraLine>,
    val paymentLines: List<ClerkStatementPaymentLine>,
    val earned: Long,
    val extras: Long,
    val paid: Long,
) {
    /** owed = earned + extras − paid, same convention as [com.vague.crewtally.balance.BalanceCalculator.owed]. */
    val owed: Long get() = earned + extras - paid

    /** Present-day count — the "12 days" half of the "12 days x $25" summary line. */
    val presentDayCount: Int get() = dayLines.count { it.present }
}

// --- Company statement (LOCKED Phase 5 report #2, EXTENDED v1.1 with dated activity lines) --

/** One project's identity plus every clerk's raw ledger on it, the pure input to [CompanyTotalsBuilder]. */
data class CompanyReportProjectInput(
    val projectId: String,
    val projectName: String,
    val currency: String,
    val clerks: List<ReportClerkLedgerInput>,
)

/** One clerk row inside a project section of the company statement. */
data class CompanyProjectClerkRow(val clerkName: String, val earned: Long, val paid: Long, val owed: Long)

/**
 * One dated line inside a project section's per-clerk-per-day activity list (v1.1 — every
 * subtype shares [date] so the whole list sorts chronologically as one timeline, and [clerkName]
 * so same-date lines break ties deterministically rather than by insertion order).
 */
sealed interface CompanyActivityLine {
    val date: LocalDate
    val clerkName: String
}

/** One clerk's one PRESENT day: [amount] is that day's rateSnapshot (LOCKED v1.1 wording: "date, clerk name, that day's rateSnapshot amount"). */
data class CompanyDayActivityLine(
    override val date: LocalDate,
    override val clerkName: String,
    val amount: Long,
) : CompanyActivityLine

/** One extra-pay line, signed ([amount] negative = deduction — LOCKED). */
data class CompanyExtraActivityLine(
    override val date: LocalDate,
    override val clerkName: String,
    val label: String,
    val amount: Long,
) : CompanyActivityLine

/** One payment, [amount] always negative (a payment reduces what's owed — LOCKED v1.1 wording: "negative amount"). */
data class CompanyPaymentActivityLine(
    override val date: LocalDate,
    override val clerkName: String,
    val amount: Long,
    val note: String,
) : CompanyActivityLine

/**
 * One project's breakdown inside the company statement: the existing per-clerk earned/paid/owed
 * summary table ([clerkRows]), the v1.1 dated [activityLines] beneath it, then the project's own
 * subtotal.
 */
data class CompanyProjectSection(
    val projectName: String,
    val currency: String,
    val clerkRows: List<CompanyProjectClerkRow>,
    val activityLines: List<CompanyActivityLine>,
    val projectEarned: Long,
    val projectPaid: Long,
    val projectOwed: Long,
)

/** One currency's grand total across every project section (cross-currency amounts are NEVER summed — LOCKED). */
data class CompanyCurrencyTotal(val currency: String, val earned: Long, val paid: Long, val owed: Long)

/**
 * The full company statement (LOCKED Phase 5 report #2, EXTENDED v1.1): every project broken
 * down by clerk plus dated activity lines, [range]-scoped throughout, plus grand totals grouped
 * per currency. A company with no projects yields empty lists throughout (the empty-project
 * edge case) rather than a null/missing report.
 */
data class CompanyTotals(
    val companyName: String,
    val range: ReportDateRange,
    val projectSections: List<CompanyProjectSection>,
    val grandTotalsByCurrency: List<CompanyCurrencyTotal>,
)

// --- Project statement (NEW v1.1 report) ----------------------------------------------------

/** One clerk row on the project statement's per-clerk summary table (LOCKED v1.1 format: CLERK / DAYS / EARNED / PAID / OWED). */
data class ProjectStatementClerkRow(
    val clerkName: String,
    val daysWorked: Int,
    val earned: Long,
    val paid: Long,
    val owed: Long,
)

/**
 * The full project statement (NEW v1.1 report): a per-clerk summary table plus a PROJECT TOTAL
 * row, [range]-scoped throughout. Single currency per project, so [totalEarned]/[totalPaid]/
 * [totalOwed] are plain sums (LOCKED — no cross-currency concern within one project).
 */
data class ProjectStatement(
    val projectName: String,
    val companyName: String,
    val currency: String,
    val range: ReportDateRange,
    val clerkRows: List<ProjectStatementClerkRow>,
    val totalEarned: Long,
    val totalPaid: Long,
    val totalOwed: Long,
)

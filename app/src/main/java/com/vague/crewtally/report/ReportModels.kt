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

// --- Company totals report (LOCKED Phase 5 report #2) --------------------------------------

/** One clerk's raw money components on one project, the pure input to [CompanyTotalsBuilder]. */
data class CompanyReportClerkInput(
    val clerkId: String,
    val clerkName: String,
    val earned: Long,
    val extras: Long,
    val paid: Long,
)

/** One project's identity plus every clerk who has activity on it, the pure input to [CompanyTotalsBuilder]. */
data class CompanyReportProjectInput(
    val projectId: String,
    val projectName: String,
    val currency: String,
    val clerks: List<CompanyReportClerkInput>,
)

/** One clerk row inside a project section of the company report. */
data class CompanyProjectClerkRow(val clerkName: String, val earned: Long, val paid: Long, val owed: Long)

/** One project's breakdown inside the company report: its clerk rows plus the project's own subtotal. */
data class CompanyProjectSection(
    val projectName: String,
    val currency: String,
    val clerkRows: List<CompanyProjectClerkRow>,
    val projectEarned: Long,
    val projectPaid: Long,
    val projectOwed: Long,
)

/** One currency's grand total across every project section (cross-currency amounts are NEVER summed — LOCKED). */
data class CompanyCurrencyTotal(val currency: String, val earned: Long, val paid: Long, val owed: Long)

/**
 * The full company report (LOCKED Phase 5 report #2): every project broken down by clerk, plus
 * grand totals grouped per currency. A company with no projects yields empty lists throughout
 * (the empty-project edge case) rather than a null/missing report.
 */
data class CompanyTotals(
    val companyName: String,
    val projectSections: List<CompanyProjectSection>,
    val grandTotalsByCurrency: List<CompanyCurrencyTotal>,
)

package com.vague.crewtally.report

/**
 * Every label [ReportLines] needs, resolved from `strings.xml` by the caller (a ViewModel, via
 * `Context.getString`) before reaching this pure package — LOCKED decision #4 requires every
 * user-facing string live in resources, but [ReportLines] itself has no Android [Context] so it
 * cannot call `getString` directly. Passing a fully-resolved bundle in keeps report generation
 * pure/testable while keeping every word translator-visible.
 *
 * [owedPhraseOwedTemplate] / [owedPhraseAdvanceTemplate] are the raw `%1$s`-style templates from
 * `owed_phrase_owed` / `owed_phrase_advance` (fetched WITHOUT formatting args), so [ReportLines]
 * can format them itself once it has computed the money string.
 */
data class ReportStrings(
    val appName: String,
    val clerkStatementTitle: String,
    val companyReportTitle: String,
    val daysWorkedLabel: String,
    val extrasLabel: String,
    val paymentsLabel: String,
    val earnedLabel: String,
    val paidLabel: String,
    val owedLabel: String,
    val deductionLabel: String,
    val presentLabel: String,
    val absentLabel: String,
    val noneRecordedLabel: String,
    val projectLabel: String,
    val grandTotalLabel: String,
    val owedPhraseOwedTemplate: String,
    val owedPhraseAdvanceTemplate: String,
    val owedPhraseSettled: String,
    val summaryLineTemplate: String,
    /**
     * The summary line's fallback wording when the clerk's present days DON'T all share one
     * rate (a mid-project rate edit) — [summaryLineTemplate]'s "N days x RATE" multiplier would
     * misstate the total in that case, so this drops the per-day rate and states the earned
     * total directly instead.
     */
    val summaryLineVaryingRateTemplate: String,
    /** Raw `%1$d`/`%2$d` template for the PDF footer, e.g. "Page %1$d of %2$d". */
    val pageLabelTemplate: String,
    // --- v1.1: Project statement + extended Company statement ------------------------------
    val projectStatementTitle: String,
    /** The LOCKED v1.1 literal column header: "CLERK / DAYS / EARNED / PAID / OWED". */
    val projectStatementHeaderLabel: String,
    /** The LOCKED v1.1 literal totals-line label: "PROJECT TOTAL". */
    val projectTotalLabel: String,
    /** The header wording when [com.vague.crewtally.report.ReportDateRange.isAllTime]. */
    val allTimeLabel: String,
    /** Suffix on a company statement's present-day activity line, e.g. "Ali Hassan - day". */
    val dayActivityLabel: String,
    /** Raw `%1$s` template for a payment activity line, e.g. "Payment -> %1$s". */
    val paymentArrowTemplate: String,
)

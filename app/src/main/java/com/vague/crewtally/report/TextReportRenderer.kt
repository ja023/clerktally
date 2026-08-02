package com.vague.crewtally.report

/**
 * Joins [ReportLines] output into the single plain-text message the "Share as text" action
 * hands to the system share sheet — WhatsApp-clean (LOCKED Phase 5): short lines, no wide
 * tables, one string, ready to paste into a chat.
 */
object TextReportRenderer {

    fun renderClerkStatement(statement: ClerkStatement, strings: ReportStrings): String =
        ReportLines.forClerkStatement(statement, strings).joinToString("\n")

    fun renderCompanyTotals(totals: CompanyTotals, strings: ReportStrings): String =
        ReportLines.forCompanyTotals(totals, strings).joinToString("\n")
}

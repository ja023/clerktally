package com.vague.crewtally.report

/**
 * Builds a [ClerkMultiProjectStatement] (NEW v1.2) from one clerk's raw ledger on every project
 * they ever worked, keeping only the projects in the requested [ClerkProjectBucket]. Pure
 * Kotlin — no Room/Android — so bucket membership, per-currency grouping, range filtering, and
 * the empty-bucket edge case are all unit-testable without a database.
 *
 * Every per-project section is produced by [ClerkStatementBuilder] (which in turn totals through
 * [com.vague.crewtally.balance.BalanceCalculator]), so a section here, the standalone per-project
 * statement, and the balance screen can never drift apart.
 */
object ClerkMultiProjectStatementBuilder {

    fun build(
        clerkName: String,
        bucket: ClerkProjectBucket,
        projects: List<ClerkProjectLedgerInput>,
        range: ReportDateRange = ReportDateRange.ALL_TIME,
    ): ClerkMultiProjectStatement {
        val sections = projects
            .filter { bucket.includes(it.status) }
            .mapNotNull { project -> sectionFor(project, clerkName, range) }
            .sortedBy { it.statement.projectName.lowercase() }

        return ClerkMultiProjectStatement(
            clerkName = clerkName,
            bucket = bucket,
            range = range,
            sections = sections,
            grandTotalsByCurrency = grandTotalsByCurrency(sections),
        )
    }

    /**
     * One project's section, or null when the clerk has no row at all on it once [range] is
     * applied — a project worked outside the selected window drops off the statement entirely
     * (LOCKED v1.1: "all totals reflect the selected range only") rather than printing an
     * all-zero section.
     */
    private fun sectionFor(
        project: ClerkProjectLedgerInput,
        clerkName: String,
        range: ReportDateRange,
    ): ClerkMultiProjectSection? {
        val attendance = project.attendance.filter { range.contains(it.date) }
        val extras = project.extras.filter { range.contains(it.date) }
        val payments = project.payments.filter { range.contains(it.date) }
        if (attendance.isEmpty() && extras.isEmpty() && payments.isEmpty()) return null

        return ClerkMultiProjectSection(
            projectId = project.projectId,
            statement = ClerkStatementBuilder.build(
                clerkName = clerkName,
                projectName = project.projectName,
                companyName = project.companyName,
                currency = project.currency,
                attendance = attendance,
                extras = extras,
                payments = payments,
            ),
        )
    }

    /**
     * Cross-currency amounts are NEVER summed (LOCKED) — group the sections by currency first,
     * then sum within each group, mirroring [CompanyTotalsBuilder]'s approach.
     */
    private fun grandTotalsByCurrency(sections: List<ClerkMultiProjectSection>): List<ClerkCurrencyTotal> = sections
        .groupBy { it.statement.currency }
        .filterKeys { it.isNotEmpty() }
        .map { (currency, group) ->
            ClerkCurrencyTotal(
                currency = currency,
                earned = group.sumOf { it.statement.earned },
                extras = group.sumOf { it.statement.extras },
                paid = group.sumOf { it.statement.paid },
            )
        }
        .sortedBy { it.currency }
}

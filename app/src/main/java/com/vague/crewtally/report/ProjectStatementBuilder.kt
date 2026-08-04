package com.vague.crewtally.report

import com.vague.crewtally.balance.BalanceCalculator

/**
 * Builds a [ProjectStatement] (NEW v1.1 report) from the raw per-clerk ledgers of every clerk who
 * ever had activity on the project. Pure Kotlin, no Room/Android — the [ReportDateRange] filter
 * is applied here so it is fully unit-testable without a database (LOCKED v1.1: "implement
 * filtering in the pure builders").
 */
object ProjectStatementBuilder {

    fun build(
        projectName: String,
        companyName: String,
        currency: String,
        clerks: List<ReportClerkLedgerInput>,
        range: ReportDateRange = ReportDateRange.ALL_TIME,
    ): ProjectStatement {
        val clerkRows = clerks
            .map { it.filteredBy(range) }
            .filter { it.hasActivity }
            .map { ledger ->
                val earned = BalanceCalculator.earnedFrom(ledger.attendance) +
                    BalanceCalculator.extrasFrom(ledger.extras.map { it.line })
                val paid = BalanceCalculator.paidFrom(ledger.payments)
                ProjectStatementClerkRow(
                    clerkName = ledger.clerkName,
                    daysWorked = ledger.attendance.count { it.present },
                    earned = earned,
                    paid = paid,
                    owed = earned - paid,
                )
            }
            .sortedBy { it.clerkName.lowercase() }

        return ProjectStatement(
            projectName = projectName,
            companyName = companyName,
            currency = currency,
            range = range,
            clerkRows = clerkRows,
            totalEarned = clerkRows.sumOf { it.earned },
            totalPaid = clerkRows.sumOf { it.paid },
            totalOwed = clerkRows.sumOf { it.owed },
        )
    }
}

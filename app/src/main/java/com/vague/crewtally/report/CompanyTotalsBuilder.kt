package com.vague.crewtally.report

import com.vague.crewtally.balance.BalanceCalculator

/**
 * Builds a [CompanyTotals] report from a company's projects, each carrying every clerk's raw
 * ledger on it ([ReportClerkLedgerInput], assembled by the caller from the raw DAO flows,
 * mirroring the pattern [com.vague.crewtally.ui.viewmodel.HomeViewModel] uses for cross-project
 * roll-ups). Pure Kotlin — no Room/Android — so the currency-grouping, date-range filtering, and
 * empty-input edge cases are fully unit-testable without a database.
 *
 * EXTENDED v1.1: alongside the existing per-clerk earned/paid/owed summary table, each project
 * section now also carries per-clerk-per-day [CompanyActivityLine]s, and the whole report is
 * scoped to [range] (LOCKED default All time).
 */
object CompanyTotalsBuilder {

    fun build(
        companyName: String,
        projects: List<CompanyReportProjectInput>,
        range: ReportDateRange = ReportDateRange.ALL_TIME,
    ): CompanyTotals {
        val projectSections = projects.map { project ->
            val filteredLedgers = project.clerks
                .map { it.filteredBy(range) }
                .filter { it.hasActivity }

            val clerkRows = filteredLedgers
                .map { ledger ->
                    val earned = BalanceCalculator.earnedFrom(ledger.attendance) +
                        BalanceCalculator.extrasFrom(ledger.extras.map { it.line })
                    val paid = BalanceCalculator.paidFrom(ledger.payments)
                    CompanyProjectClerkRow(
                        clerkName = ledger.clerkName,
                        earned = earned,
                        paid = paid,
                        owed = earned - paid,
                    )
                }
                .sortedBy { it.clerkName.lowercase() }

            val activityLines = filteredLedgers
                .flatMap { ledger ->
                    val dayLines = ledger.attendance
                        .filter { it.present }
                        .map { CompanyDayActivityLine(date = it.date, clerkName = ledger.clerkName, amount = it.rateSnapshot) }
                    val extraLines = ledger.extras
                        .map {
                            CompanyExtraActivityLine(
                                date = it.date,
                                clerkName = ledger.clerkName,
                                label = it.line.label,
                                amount = it.line.amount,
                            )
                        }
                    // A payment always reduces what's owed, so its activity-line amount is the
                    // NEGATIVE of the stored (always-positive) PaymentEntity.amount — LOCKED v1.1
                    // wording: "Payment -> clerk, negative amount".
                    val paymentLines = ledger.payments
                        .map {
                            CompanyPaymentActivityLine(
                                date = it.date,
                                clerkName = ledger.clerkName,
                                amount = -it.amount,
                                note = it.note,
                            )
                        }
                    dayLines + extraLines + paymentLines
                }
                .sortedWith(compareBy({ it.date }, { it.clerkName.lowercase() }))

            CompanyProjectSection(
                projectName = project.projectName,
                currency = project.currency,
                clerkRows = clerkRows,
                activityLines = activityLines,
                projectEarned = clerkRows.sumOf { it.earned },
                projectPaid = clerkRows.sumOf { it.paid },
                projectOwed = clerkRows.sumOf { it.owed },
            )
        }

        // Cross-currency amounts are NEVER summed (LOCKED) — group by currency first, then sum
        // within each group, mirroring BalanceCalculator.currencyTotals' approach.
        val grandTotalsByCurrency = projectSections
            .groupBy { it.currency }
            .filterKeys { it.isNotEmpty() }
            .map { (currency, sections) ->
                CompanyCurrencyTotal(
                    currency = currency,
                    earned = sections.sumOf { it.projectEarned },
                    paid = sections.sumOf { it.projectPaid },
                    owed = sections.sumOf { it.projectOwed },
                )
            }
            .sortedBy { it.currency }

        return CompanyTotals(
            companyName = companyName,
            range = range,
            projectSections = projectSections,
            grandTotalsByCurrency = grandTotalsByCurrency,
        )
    }
}

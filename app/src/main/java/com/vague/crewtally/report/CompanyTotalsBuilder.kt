package com.vague.crewtally.report

/**
 * Builds a [CompanyTotals] report from a company's projects, each carrying the raw per-clerk
 * money components ([CompanyReportClerkInput]) the caller already assembled from the money
 * roll-up DAOs (mirroring the pattern [com.vague.crewtally.ui.viewmodel.HomeViewModel] uses).
 * Pure Kotlin — no Room/Android — so the currency-grouping and empty-input edge cases are fully
 * unit-testable without a database.
 */
object CompanyTotalsBuilder {

    fun build(companyName: String, projects: List<CompanyReportProjectInput>): CompanyTotals {
        val projectSections = projects.map { project ->
            val clerkRows = project.clerks
                .map { clerk ->
                    CompanyProjectClerkRow(
                        clerkName = clerk.clerkName,
                        earned = clerk.earned + clerk.extras,
                        paid = clerk.paid,
                        owed = clerk.earned + clerk.extras - clerk.paid,
                    )
                }
                .sortedBy { it.clerkName.lowercase() }
            CompanyProjectSection(
                projectName = project.projectName,
                currency = project.currency,
                clerkRows = clerkRows,
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
            projectSections = projectSections,
            grandTotalsByCurrency = grandTotalsByCurrency,
        )
    }
}

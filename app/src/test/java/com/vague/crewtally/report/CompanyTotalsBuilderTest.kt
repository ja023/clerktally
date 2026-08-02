package com.vague.crewtally.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CompanyTotalsBuilder]'s two hard rules: per-project clerk rows compute earned/paid/owed
 * correctly, and grand totals are grouped per currency — NEVER summed across currencies
 * (LOCKED) — plus the empty-project/empty-clerk edge cases.
 */
class CompanyTotalsBuilderTest {

    @Test
    fun `a clerk row's earned is gross (days plus extras) and owed subtracts paid`() {
        val input = CompanyReportProjectInput(
            projectId = "p1",
            projectName = "Warehouse Count",
            currency = "USD",
            clerks = listOf(
                CompanyReportClerkInput(clerkId = "c1", clerkName = "Ali", earned = 5000, extras = 1000, paid = 2000),
            ),
        )

        val totals = CompanyTotalsBuilder.build("Acme", listOf(input))
        val row = totals.projectSections.single().clerkRows.single()

        assertEquals(6000L, row.earned) // 5000 + 1000
        assertEquals(2000L, row.paid)
        assertEquals(4000L, row.owed) // 6000 - 2000
    }

    @Test
    fun `a project's subtotal sums its clerk rows`() {
        val input = CompanyReportProjectInput(
            projectId = "p1",
            projectName = "Warehouse Count",
            currency = "USD",
            clerks = listOf(
                CompanyReportClerkInput("c1", "Ali", earned = 5000, extras = 0, paid = 5000),
                CompanyReportClerkInput("c2", "Sara", earned = 3000, extras = 0, paid = 0),
            ),
        )

        val section = CompanyTotalsBuilder.build("Acme", listOf(input)).projectSections.single()

        assertEquals(8000L, section.projectEarned)
        assertEquals(5000L, section.projectPaid)
        assertEquals(3000L, section.projectOwed)
    }

    @Test
    fun `grand totals group by currency and never sum across currencies`() {
        val usdProject = CompanyReportProjectInput(
            "p1", "USD Project", "USD",
            listOf(CompanyReportClerkInput("c1", "Ali", earned = 5000, extras = 0, paid = 0)),
        )
        val lbpProject = CompanyReportProjectInput(
            "p2", "LBP Project", "LBP",
            listOf(CompanyReportClerkInput("c2", "Sara", earned = 900000, extras = 0, paid = 0)),
        )

        val totals = CompanyTotalsBuilder.build("Acme", listOf(usdProject, lbpProject))

        assertEquals(2, totals.grandTotalsByCurrency.size)
        val usdTotal = totals.grandTotalsByCurrency.single { it.currency == "USD" }
        val lbpTotal = totals.grandTotalsByCurrency.single { it.currency == "LBP" }
        assertEquals(5000L, usdTotal.earned)
        assertEquals(900000L, lbpTotal.earned)
        // Neither total ever contains the other currency's magnitude — proves they were never summed together.
        assertTrue(usdTotal.earned != 5000L + 900000L)
    }

    @Test
    fun `two projects sharing a currency ARE summed together into one grand total`() {
        val project1 = CompanyReportProjectInput(
            "p1", "Project 1", "USD",
            listOf(CompanyReportClerkInput("c1", "Ali", earned = 5000, extras = 0, paid = 0)),
        )
        val project2 = CompanyReportProjectInput(
            "p2", "Project 2", "USD",
            listOf(CompanyReportClerkInput("c2", "Sara", earned = 3000, extras = 0, paid = 0)),
        )

        val totals = CompanyTotalsBuilder.build("Acme", listOf(project1, project2))

        assertEquals(1, totals.grandTotalsByCurrency.size)
        assertEquals(8000L, totals.grandTotalsByCurrency.single().earned)
    }

    @Test
    fun `a company with no projects yields empty sections and empty grand totals`() {
        val totals = CompanyTotalsBuilder.build("Acme", emptyList())

        assertTrue(totals.projectSections.isEmpty())
        assertTrue(totals.grandTotalsByCurrency.isEmpty())
    }

    @Test
    fun `a project with no clerk activity yields an empty clerk-row section with zero subtotal`() {
        val input = CompanyReportProjectInput("p1", "Empty Project", "USD", emptyList())

        val section = CompanyTotalsBuilder.build("Acme", listOf(input)).projectSections.single()

        assertTrue(section.clerkRows.isEmpty())
        assertEquals(0L, section.projectEarned)
        assertEquals(0L, section.projectOwed)
    }
}

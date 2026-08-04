package com.vague.crewtally.report

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [CompanyTotalsBuilder]'s rules (LOCKED Phase 5 + EXTENDED v1.1): per-project clerk rows
 * compute earned/paid/owed correctly, grand totals are grouped per currency — NEVER summed
 * across currencies, per-clerk-per-day activity lines are chronological, date-range filtering
 * excludes out-of-range rows from both the summary table and the totals, and the empty-project/
 * empty-clerk edge cases still hold.
 */
class CompanyTotalsBuilderTest {

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan3 = LocalDate.of(2026, 1, 3)
    private val jan5 = LocalDate.of(2026, 1, 5)
    private val feb1 = LocalDate.of(2026, 2, 1)

    private fun clerk(
        clerkId: String,
        clerkName: String,
        attendance: List<AttendanceEntryEntity> = emptyList(),
        extras: List<ExtraPayLineWithDate> = emptyList(),
        payments: List<PaymentEntity> = emptyList(),
    ) = ReportClerkLedgerInput(clerkId, clerkName, attendance, extras, payments)

    @Test
    fun `a clerk row's earned is gross (days plus extras) and owed subtracts paid`() {
        val ali = clerk(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Bonus", 1000), jan1)),
            payments = listOf(PaymentEntity("pay1", "p1", "c1", jan1, 2000, "")),
        )
        val input = CompanyReportProjectInput("p1", "Warehouse Count", "USD", listOf(ali))

        val totals = CompanyTotalsBuilder.build("Acme", listOf(input))
        val row = totals.projectSections.single().clerkRows.single()

        assertEquals(6000L, row.earned) // 5000 + 1000
        assertEquals(2000L, row.paid)
        assertEquals(4000L, row.owed) // 6000 - 2000
    }

    @Test
    fun `a project's subtotal sums its clerk rows`() {
        val ali = clerk("c1", "Ali", attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)), payments = listOf(PaymentEntity("pay1", "p1", "c1", jan1, 5000, "")))
        val sara = clerk("c2", "Sara", attendance = listOf(AttendanceEntryEntity("a2", "p1", "c2", jan1, present = true, rateSnapshot = 3000)))
        val input = CompanyReportProjectInput("p1", "Warehouse Count", "USD", listOf(ali, sara))

        val section = CompanyTotalsBuilder.build("Acme", listOf(input)).projectSections.single()

        assertEquals(8000L, section.projectEarned)
        assertEquals(5000L, section.projectPaid)
        assertEquals(3000L, section.projectOwed)
    }

    @Test
    fun `grand totals group by currency and never sum across currencies`() {
        val ali = clerk("c1", "Ali", attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)))
        val sara = clerk("c2", "Sara", attendance = listOf(AttendanceEntryEntity("a2", "p2", "c2", jan1, present = true, rateSnapshot = 900000)))
        val usdProject = CompanyReportProjectInput("p1", "USD Project", "USD", listOf(ali))
        val lbpProject = CompanyReportProjectInput("p2", "LBP Project", "LBP", listOf(sara))

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
        val ali = clerk("c1", "Ali", attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)))
        val sara = clerk("c2", "Sara", attendance = listOf(AttendanceEntryEntity("a2", "p2", "c2", jan1, present = true, rateSnapshot = 3000)))
        val project1 = CompanyReportProjectInput("p1", "Project 1", "USD", listOf(ali))
        val project2 = CompanyReportProjectInput("p2", "Project 2", "USD", listOf(sara))

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
        assertTrue(section.activityLines.isEmpty())
        assertEquals(0L, section.projectEarned)
        assertEquals(0L, section.projectOwed)
    }

    @Test
    fun `activity lines cover one present day, one extra, and one payment, chronologically`() {
        val ali = clerk(
            "c1",
            "Ali",
            attendance = listOf(
                AttendanceEntryEntity("a1", "p1", "c1", jan5, present = true, rateSnapshot = 2500),
                AttendanceEntryEntity("a2", "p1", "c1", jan1, present = false, rateSnapshot = 2500),
            ),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Bonus", 1000), jan3)),
            payments = listOf(PaymentEntity("pay1", "p1", "c1", jan1, 500, "part payment")),
        )
        val input = CompanyReportProjectInput("p1", "Warehouse Count", "USD", listOf(ali))

        val lines = CompanyTotalsBuilder.build("Acme", listOf(input)).projectSections.single().activityLines

        // Absent day (jan1 present=false) yields NO day line, but the payment on jan1 still does.
        assertEquals(3, lines.size)
        assertEquals(listOf(jan1, jan3, jan5), lines.map { it.date })
        assertTrue(lines[0] is CompanyPaymentActivityLine)
        assertTrue(lines[1] is CompanyExtraActivityLine)
        assertTrue(lines[2] is CompanyDayActivityLine)
    }

    @Test
    fun `a present day's activity-line amount is that day's rateSnapshot`() {
        val ali = clerk("c1", "Ali", attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 4500)))
        val input = CompanyReportProjectInput("p1", "Project", "USD", listOf(ali))

        val line = CompanyTotalsBuilder.build("Acme", listOf(input)).projectSections.single().activityLines.single() as CompanyDayActivityLine

        assertEquals(4500L, line.amount)
        assertEquals("Ali", line.clerkName)
    }

    @Test
    fun `a deduction extra keeps its negative amount on the activity line`() {
        val ali = clerk("c1", "Ali", extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Damage", -2000), jan1)))
        val input = CompanyReportProjectInput("p1", "Project", "USD", listOf(ali))

        val line = CompanyTotalsBuilder.build("Acme", listOf(input)).projectSections.single().activityLines.single() as CompanyExtraActivityLine

        assertEquals(-2000L, line.amount)
    }

    @Test
    fun `a payment's activity-line amount is always negative, even though PaymentEntity amount is always positive`() {
        val ali = clerk("c1", "Ali", payments = listOf(PaymentEntity("pay1", "p1", "c1", jan1, 10000, "")))
        val input = CompanyReportProjectInput("p1", "Project", "USD", listOf(ali))

        val line = CompanyTotalsBuilder.build("Acme", listOf(input)).projectSections.single().activityLines.single() as CompanyPaymentActivityLine

        assertEquals(-10000L, line.amount)
    }

    @Test
    fun `a date range excludes out-of-range rows from both the clerk row and the activity lines`() {
        val ali = clerk(
            "c1",
            "Ali",
            attendance = listOf(
                AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000),
                AttendanceEntryEntity("a2", "p1", "c1", feb1, present = true, rateSnapshot = 5000),
            ),
        )
        val input = CompanyReportProjectInput("p1", "Project", "USD", listOf(ali))
        val januaryOnly = ReportDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))

        val section = CompanyTotalsBuilder.build("Acme", listOf(input), januaryOnly).projectSections.single()

        assertEquals(5000L, section.clerkRows.single().earned)
        assertEquals(listOf(jan1), section.activityLines.map { it.date })
    }

    @Test
    fun `a clerk with no activity inside the selected range drops off the section entirely`() {
        val ali = clerk("c1", "Ali", attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", feb1, present = true, rateSnapshot = 5000)))
        val input = CompanyReportProjectInput("p1", "Project", "USD", listOf(ali))
        val januaryOnly = ReportDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))

        val section = CompanyTotalsBuilder.build("Acme", listOf(input), januaryOnly).projectSections.single()

        assertTrue(section.clerkRows.isEmpty())
        assertTrue(section.activityLines.isEmpty())
    }
}

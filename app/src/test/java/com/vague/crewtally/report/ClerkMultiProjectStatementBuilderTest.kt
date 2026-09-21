package com.vague.crewtally.report

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ClerkMultiProjectStatementBuilder] (NEW v1.2): bucket membership, per-currency
 * grand totals (never summed across currencies — LOCKED), the empty-bucket case, and date-range
 * filtering of days, extras, AND payments (same rule as v1.1).
 */
class ClerkMultiProjectStatementBuilderTest {

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)
    private val feb1 = LocalDate.of(2026, 2, 1)

    private fun project(
        id: String,
        name: String,
        status: ProjectStatus,
        currency: String = "USD",
        attendance: List<AttendanceEntryEntity> = emptyList(),
        extras: List<ExtraPayLineWithDate> = emptyList(),
        payments: List<PaymentEntity> = emptyList(),
    ) = ClerkProjectLedgerInput(
        projectId = id,
        projectName = name,
        companyName = "Acme",
        currency = currency,
        status = status,
        attendance = attendance,
        extras = extras,
        payments = payments,
    )

    private fun day(id: String, projectId: String, date: LocalDate, rate: Long = 5000) =
        AttendanceEntryEntity(id, projectId, "c1", date, present = true, rateSnapshot = rate)

    private fun extra(id: String, date: LocalDate, amount: Long, label: String = "Transport") =
        ExtraPayLineWithDate(ExtraPayLineEntity(id, "a-$id", label, amount), date)

    private fun payment(id: String, projectId: String, date: LocalDate, amount: Long) =
        PaymentEntity(id, projectId, "c1", date, amount, "")

    @Test
    fun `the active bucket keeps only projects that are neither completed nor archived`() {
        val projects = listOf(
            project("p1", "Live", ProjectStatus.ACTIVE, attendance = listOf(day("a1", "p1", jan1))),
            project("p2", "Done", ProjectStatus.COMPLETED, attendance = listOf(day("a2", "p2", jan1))),
            project("p3", "Filed", ProjectStatus.ARCHIVED, attendance = listOf(day("a3", "p3", jan1))),
        )

        val statement = ClerkMultiProjectStatementBuilder.build("Ali", ClerkProjectBucket.ACTIVE, projects)

        assertEquals(listOf("Live"), statement.sections.map { it.statement.projectName })
    }

    @Test
    fun `the history bucket keeps completed and archived projects only`() {
        val projects = listOf(
            project("p1", "Live", ProjectStatus.ACTIVE, attendance = listOf(day("a1", "p1", jan1))),
            project("p2", "Done", ProjectStatus.COMPLETED, attendance = listOf(day("a2", "p2", jan1))),
            project("p3", "Filed", ProjectStatus.ARCHIVED, attendance = listOf(day("a3", "p3", jan1))),
        )

        val statement = ClerkMultiProjectStatementBuilder.build("Ali", ClerkProjectBucket.HISTORY, projects)

        assertEquals(listOf("Done", "Filed"), statement.sections.map { it.statement.projectName })
    }

    @Test
    fun `a project section carries the same earned extras paid owed as its per-project statement`() {
        val projects = listOf(
            project(
                id = "p1",
                name = "Warehouse",
                status = ProjectStatus.ACTIVE,
                attendance = listOf(day("a1", "p1", jan1), day("a2", "p1", jan2)),
                extras = listOf(extra("e1", jan1, 1500), extra("e2", jan2, -500, "Deduction")),
                payments = listOf(payment("pay1", "p1", jan2, 4000)),
            ),
        )

        val section = ClerkMultiProjectStatementBuilder
            .build("Ali", ClerkProjectBucket.ACTIVE, projects)
            .sections
            .single()
            .statement

        assertEquals(10000L, section.earned)
        assertEquals(1000L, section.extras)
        assertEquals(4000L, section.paid)
        assertEquals(7000L, section.owed)
        assertEquals(2, section.presentDayCount)
        assertEquals("Acme", section.companyName)
    }

    @Test
    fun `grand totals are grouped per currency and never summed across them`() {
        val projects = listOf(
            project("p1", "Dollars one", ProjectStatus.ACTIVE, currency = "USD", attendance = listOf(day("a1", "p1", jan1, 5000))),
            project("p2", "Dollars two", ProjectStatus.ACTIVE, currency = "USD", attendance = listOf(day("a2", "p2", jan1, 3000))),
            project("p3", "Euros", ProjectStatus.ACTIVE, currency = "EUR", attendance = listOf(day("a3", "p3", jan1, 2000))),
        )

        val totals = ClerkMultiProjectStatementBuilder
            .build("Ali", ClerkProjectBucket.ACTIVE, projects)
            .grandTotalsByCurrency

        assertEquals(listOf("EUR", "USD"), totals.map { it.currency })
        assertEquals(2000L, totals.first { it.currency == "EUR" }.earned)
        assertEquals(8000L, totals.first { it.currency == "USD" }.earned)
        assertEquals(8000L, totals.first { it.currency == "USD" }.owed)
    }

    @Test
    fun `a currency total folds extras in and subtracts payments`() {
        val projects = listOf(
            project(
                id = "p1",
                name = "Warehouse",
                status = ProjectStatus.ACTIVE,
                attendance = listOf(day("a1", "p1", jan1, 5000)),
                extras = listOf(extra("e1", jan1, 1000)),
                payments = listOf(payment("pay1", "p1", jan1, 2000)),
            ),
        )

        val total = ClerkMultiProjectStatementBuilder
            .build("Ali", ClerkProjectBucket.ACTIVE, projects)
            .grandTotalsByCurrency
            .single()

        assertEquals(5000L, total.earned)
        assertEquals(1000L, total.extras)
        assertEquals(2000L, total.paid)
        assertEquals(4000L, total.owed)
    }

    @Test
    fun `an empty bucket yields an empty statement rather than a null document`() {
        val projects = listOf(
            project("p1", "Done", ProjectStatus.COMPLETED, attendance = listOf(day("a1", "p1", jan1))),
        )

        val statement = ClerkMultiProjectStatementBuilder.build("Ali", ClerkProjectBucket.ACTIVE, projects)

        assertTrue(statement.isEmpty)
        assertTrue(statement.sections.isEmpty())
        assertTrue(statement.grandTotalsByCurrency.isEmpty())
        assertEquals("Ali", statement.clerkName)
        assertEquals(ClerkProjectBucket.ACTIVE, statement.bucket)
    }

    @Test
    fun `no projects at all yields an empty statement`() {
        val statement = ClerkMultiProjectStatementBuilder.build("Ali", ClerkProjectBucket.HISTORY, emptyList())

        assertTrue(statement.isEmpty)
        assertTrue(statement.grandTotalsByCurrency.isEmpty())
    }

    @Test
    fun `the range filters days, extras and payments alike`() {
        val projects = listOf(
            project(
                id = "p1",
                name = "Warehouse",
                status = ProjectStatus.ACTIVE,
                attendance = listOf(day("a1", "p1", jan1), day("a2", "p1", feb1)),
                extras = listOf(extra("e1", jan1, 1000), extra("e2", feb1, 700)),
                payments = listOf(payment("pay1", "p1", jan1, 500), payment("pay2", "p1", feb1, 900)),
            ),
        )
        val january = ReportDateRange(jan1, LocalDate.of(2026, 1, 31))

        val section = ClerkMultiProjectStatementBuilder
            .build("Ali", ClerkProjectBucket.ACTIVE, projects, january)
            .sections
            .single()
            .statement

        assertEquals(1, section.dayLines.size)
        assertEquals(1, section.extraLines.size)
        assertEquals(1, section.paymentLines.size)
        assertEquals(5000L, section.earned)
        assertEquals(1000L, section.extras)
        assertEquals(500L, section.paid)
    }

    @Test
    fun `a project with no activity inside the range drops off the statement entirely`() {
        val projects = listOf(
            project("p1", "In range", ProjectStatus.ACTIVE, attendance = listOf(day("a1", "p1", jan1))),
            project("p2", "Out of range", ProjectStatus.ACTIVE, attendance = listOf(day("a2", "p2", feb1))),
        )
        val january = ReportDateRange(jan1, LocalDate.of(2026, 1, 31))

        val statement = ClerkMultiProjectStatementBuilder.build("Ali", ClerkProjectBucket.ACTIVE, projects, january)

        assertEquals(listOf("In range"), statement.sections.map { it.statement.projectName })
        assertNull(statement.sections.firstOrNull { it.projectId == "p2" })
    }

    @Test
    fun `sections are ordered by project name, case-insensitively`() {
        val projects = listOf(
            project("p1", "zulu site", ProjectStatus.ACTIVE, attendance = listOf(day("a1", "p1", jan1))),
            project("p2", "Alpha site", ProjectStatus.ACTIVE, attendance = listOf(day("a2", "p2", jan1))),
        )

        val statement = ClerkMultiProjectStatementBuilder.build("Ali", ClerkProjectBucket.ACTIVE, projects)

        assertEquals(listOf("Alpha site", "zulu site"), statement.sections.map { it.statement.projectName })
    }

    @Test
    fun `all time keeps every row regardless of date`() {
        val projects = listOf(
            project(
                id = "p1",
                name = "Warehouse",
                status = ProjectStatus.ACTIVE,
                attendance = listOf(day("a1", "p1", jan1), day("a2", "p1", feb1)),
            ),
        )

        val statement = ClerkMultiProjectStatementBuilder.build("Ali", ClerkProjectBucket.ACTIVE, projects)

        assertEquals(2, statement.sections.single().statement.dayLines.size)
        assertEquals(ReportDateRange.ALL_TIME, statement.range)
    }
}

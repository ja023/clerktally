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
 * [ProjectStatementBuilder] (NEW v1.1 report): per-clerk day/earned/paid/owed rows sorted by
 * name, the PROJECT TOTAL sums, date-range filtering, and the empty-project edge case.
 */
class ProjectStatementBuilderTest {

    private val jan1 = LocalDate.of(2026, 1, 1)
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
    fun `a clerk row folds extras into earned and computes owed the same way as CompanyProjectClerkRow`() {
        val ali = clerk(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Bonus", 1000), jan1)),
            payments = listOf(PaymentEntity("pay1", "p1", "c1", jan5, 2000, "")),
        )

        val statement = ProjectStatementBuilder.build("Warehouse Count", "Acme", "USD", listOf(ali))
        val row = statement.clerkRows.single()

        assertEquals(1, row.daysWorked)
        assertEquals(6000L, row.earned) // 5000 + 1000
        assertEquals(2000L, row.paid)
        assertEquals(4000L, row.owed) // 6000 - 2000
    }

    @Test
    fun `clerk rows are sorted by name case-insensitively`() {
        val zed = clerk("c1", "zed", attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 1000)))
        val ali = clerk("c2", "Ali", attendance = listOf(AttendanceEntryEntity("a2", "p1", "c2", jan1, present = true, rateSnapshot = 1000)))

        val statement = ProjectStatementBuilder.build("Project", "Acme", "USD", listOf(zed, ali))

        assertEquals(listOf("Ali", "zed"), statement.clerkRows.map { it.clerkName })
    }

    @Test
    fun `the PROJECT TOTAL row sums every clerk row`() {
        val ali = clerk("c1", "Ali", attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)))
        val sara = clerk("c2", "Sara", attendance = listOf(AttendanceEntryEntity("a2", "p1", "c2", jan1, present = true, rateSnapshot = 3000)))

        val statement = ProjectStatementBuilder.build("Project", "Acme", "USD", listOf(ali, sara))

        assertEquals(8000L, statement.totalEarned)
        assertEquals(0L, statement.totalPaid)
        assertEquals(8000L, statement.totalOwed)
    }

    @Test
    fun `an empty project yields empty clerk rows and zero totals`() {
        val statement = ProjectStatementBuilder.build("Empty Project", "Acme", "USD", emptyList())

        assertTrue(statement.clerkRows.isEmpty())
        assertEquals(0L, statement.totalEarned)
        assertEquals(0L, statement.totalOwed)
    }

    @Test
    fun `a date range excludes days extras and payments outside it from both the row and the totals`() {
        val ali = clerk(
            "c1",
            "Ali",
            attendance = listOf(
                AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000),
                AttendanceEntryEntity("a2", "p1", "c1", feb1, present = true, rateSnapshot = 5000),
            ),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a2", "Bonus", 1000), feb1)),
            payments = listOf(PaymentEntity("pay1", "p1", "c1", feb1, 500, "")),
        )
        val range = ReportDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))

        val statement = ProjectStatementBuilder.build("Project", "Acme", "USD", listOf(ali), range)
        val row = statement.clerkRows.single()

        assertEquals(1, row.daysWorked) // only the Jan 1 day counts
        assertEquals(5000L, row.earned) // Feb extras excluded
        assertEquals(0L, row.paid) // Feb payment excluded
    }

    @Test
    fun `a clerk with no activity inside the selected range drops off the statement entirely`() {
        val ali = clerk(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", feb1, present = true, rateSnapshot = 5000)),
        )
        val januaryOnly = ReportDateRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))

        val statement = ProjectStatementBuilder.build("Project", "Acme", "USD", listOf(ali), januaryOnly)

        assertTrue(statement.clerkRows.isEmpty())
        assertEquals(0L, statement.totalEarned)
    }

    @Test
    fun `a negative extra reduces earned and owed the same as a deduction elsewhere in the app`() {
        val ali = clerk(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Damage", -1500), jan1)),
        )

        val statement = ProjectStatementBuilder.build("Project", "Acme", "USD", listOf(ali))
        val row = statement.clerkRows.single()

        assertEquals(3500L, row.earned) // 5000 - 1500
        assertEquals(3500L, row.owed)
    }
}

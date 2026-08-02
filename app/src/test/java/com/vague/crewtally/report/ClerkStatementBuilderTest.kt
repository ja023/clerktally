package com.vague.crewtally.report

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ClerkStatementBuilder] must agree with [com.vague.crewtally.balance.BalanceCalculator] on
 * every total — a statement that disagreed with the balance screen would be worse than no
 * statement at all — plus the empty-clerk edge case (no ledger rows at all).
 */
class ClerkStatementBuilderTest {

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan5 = LocalDate.of(2026, 1, 5)
    private val jan10 = LocalDate.of(2026, 1, 10)

    @Test
    fun `earned extras and paid match BalanceCalculator's own math`() {
        val attendance = listOf(
            AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 5000),
            AttendanceEntryEntity("a2", "p", "c", jan5, present = false, rateSnapshot = 5000),
        )
        val extras = listOf(
            ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Bonus", 3000), jan1),
            ExtraPayLineWithDate(ExtraPayLineEntity("x2", "a1", "Fine", -1000), jan1),
        )
        val payments = listOf(PaymentEntity("pay1", "p", "c", jan10, 2000, ""))

        val statement = ClerkStatementBuilder.build(
            clerkName = "Ali",
            projectName = "Warehouse Count",
            companyName = "Acme",
            currency = "USD",
            attendance = attendance,
            extras = extras,
            payments = payments,
        )

        assertEquals(5000L, statement.earned)
        assertEquals(2000L, statement.extras) // 3000 - 1000
        assertEquals(2000L, statement.paid)
        assertEquals(5000L, statement.owed) // 5000 + 2000 - 2000
        assertEquals(1, statement.presentDayCount)
    }

    @Test
    fun `day lines carry present flag and rate snapshot sorted by date`() {
        val attendance = listOf(
            AttendanceEntryEntity("a2", "p", "c", jan5, present = true, rateSnapshot = 4500),
            AttendanceEntryEntity("a1", "p", "c", jan1, present = false, rateSnapshot = 4500),
        )

        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), emptyList())

        assertEquals(listOf(jan1, jan5), statement.dayLines.map { it.date })
        assertEquals(false, statement.dayLines[0].present)
        assertEquals(true, statement.dayLines[1].present)
        assertEquals(4500L, statement.dayLines[1].rateSnapshot)
    }

    @Test
    fun `deduction extra lines keep their negative amount`() {
        val extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Damage", -1500), jan1))

        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", emptyList(), extras, emptyList())

        assertEquals(-1500L, statement.extraLines.single().amount)
        assertEquals(-1500L, statement.extras)
    }

    @Test
    fun `a clerk with no activity at all yields empty lines, zero totals, and a null date range`() {
        val statement = ClerkStatementBuilder.build(
            clerkName = "New Clerk",
            projectName = "Project",
            companyName = "Acme",
            currency = "USD",
            attendance = emptyList(),
            extras = emptyList(),
            payments = emptyList(),
        )

        assertTrue(statement.dayLines.isEmpty())
        assertTrue(statement.extraLines.isEmpty())
        assertTrue(statement.paymentLines.isEmpty())
        assertEquals(0L, statement.earned)
        assertEquals(0L, statement.owed)
        assertNull(statement.rangeStart)
        assertNull(statement.rangeEnd)
    }

    @Test
    fun `date range spans the earliest and latest activity across all three ledgers`() {
        val attendance = listOf(AttendanceEntryEntity("a1", "p", "c", jan5, present = true, rateSnapshot = 1000))
        val payments = listOf(PaymentEntity("pay1", "p", "c", jan10, 1000, ""))

        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), payments)

        assertEquals(jan5, statement.rangeStart)
        assertEquals(jan10, statement.rangeEnd)
    }
}

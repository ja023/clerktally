package com.vague.crewtally.balance

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkProjectAmount
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.PaymentEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heart of the app under test: the pure, storage-free balance math. Covers earnings from
 * present days × rate snapshots, signed extras, payments subtracted, the advance (negative)
 * case, the per-(project, clerk) roll-up fold, and per-currency grouping that NEVER sums across
 * currencies.
 */
class BalanceCalculatorTest {

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)

    private fun attendance(present: Boolean, rate: Long, date: LocalDate = jan1) =
        AttendanceEntryEntity("a-${date}-$present-$rate", "p", "c", date, present, rate)

    @Test
    fun `earned sums rate snapshots over present days only`() {
        val entries = listOf(
            attendance(present = true, rate = 5000, date = jan1),
            attendance(present = false, rate = 5000, date = jan2),
            attendance(present = true, rate = 4000, date = LocalDate.of(2026, 1, 3)),
        )
        assertEquals(9000L, BalanceCalculator.earnedFrom(entries))
    }

    @Test
    fun `earned respects a mix of different rate snapshots`() {
        val entries = listOf(
            attendance(present = true, rate = 5000, date = jan1),
            attendance(present = true, rate = 7500, date = jan2),
        )
        assertEquals(12500L, BalanceCalculator.earnedFrom(entries))
    }

    @Test
    fun `extras sum signed amounts including deductions`() {
        val lines = listOf(
            ExtraPayLineEntity("x1", "a", "Lunch", 1000),
            ExtraPayLineEntity("x2", "a", "Fine", -300),
        )
        assertEquals(700L, BalanceCalculator.extrasFrom(lines))
    }

    @Test
    fun `owed is earned plus extras minus paid`() {
        assertEquals(2700L, BalanceCalculator.owed(earned = 9000, extras = 700, paid = 7000))
    }

    @Test
    fun `owed goes negative when overpaid (an advance)`() {
        assertEquals(-1300L, BalanceCalculator.owed(earned = 5000, extras = 0, paid = 6300))
    }

    @Test
    fun `statusOf classifies owed settled and advance`() {
        assertEquals(OwedStatus.OWED, BalanceCalculator.statusOf(130))
        assertEquals(OwedStatus.SETTLED, BalanceCalculator.statusOf(0))
        assertEquals(OwedStatus.ADVANCE, BalanceCalculator.statusOf(-50))
    }

    @Test
    fun `isAdvance is true only when the amount exceeds the owed before this payment`() {
        assertTrue(BalanceCalculator.isAdvance(amount = 6000, owedBeforeThisPayment = 5000))
        assertFalse(BalanceCalculator.isAdvance(amount = 5000, owedBeforeThisPayment = 5000))
        assertFalse(BalanceCalculator.isAdvance(amount = 3000, owedBeforeThisPayment = 5000))
        // Paying anything positive into a settled or advanced balance is itself an advance.
        assertTrue(BalanceCalculator.isAdvance(amount = 100, owedBeforeThisPayment = 0))
        assertTrue(BalanceCalculator.isAdvance(amount = 100, owedBeforeThisPayment = -500))
    }

    @Test
    fun `owedAfterChange moves owed by old minus new amount`() {
        // Editing a payment down from 5000 to 3000 raises owed by 2000.
        assertEquals(2000L, BalanceCalculator.owedAfterChange(currentOwed = 0, oldAmount = 5000, newAmount = 3000))
        // Deleting a 5000 payment (newAmount 0) raises owed by 5000.
        assertEquals(5000L, BalanceCalculator.owedAfterChange(currentOwed = 0, oldAmount = 5000, newAmount = 0))
    }

    @Test
    fun `rollup folds three component lists into one balance per pair`() {
        val earnings = listOf(ClerkProjectAmount("p1", "c1", 9000), ClerkProjectAmount("p1", "c2", 5000))
        val extras = listOf(ClerkProjectAmount("p1", "c1", 700))
        val payments = listOf(ClerkProjectAmount("p1", "c1", 7000))

        val balances = BalanceCalculator.rollup(earnings, extras, payments).associateBy { it.clerkId }

        assertEquals(2, balances.size)
        assertEquals(2700L, balances.getValue("c1").owed) // 9000 + 700 - 7000
        assertEquals(5000L, balances.getValue("c2").owed) // 5000 + 0 - 0
    }

    @Test
    fun `rollup includes a pair present in only the extras component`() {
        // A carrier row with extras but no present days and no payments still surfaces.
        val balances = BalanceCalculator.rollup(
            earnings = emptyList(),
            extras = listOf(ClerkProjectAmount("p1", "c9", -500)),
            payments = emptyList(),
        )
        assertEquals(1, balances.size)
        assertEquals(-500L, balances.single().owed)
    }

    @Test
    fun `currencyTotals keeps currencies separate and never sums across them`() {
        val balances = listOf(
            ClerkProjectBalance("pUsd", "c1", earned = 9000, extras = 500, paid = 4000),
            ClerkProjectBalance("pEur", "c1", earned = 6000, extras = 0, paid = 1000),
        )
        val totals = BalanceCalculator.currencyTotals(
            balances,
            currencyByProject = mapOf("pUsd" to "USD", "pEur" to "EUR"),
        ).associateBy { it.currency }

        assertEquals(2, totals.size)
        // USD: earned gross 9500, paid 4000, owed 5500.
        assertEquals(9500L, totals.getValue("USD").earned)
        assertEquals(4000L, totals.getValue("USD").paid)
        assertEquals(5500L, totals.getValue("USD").owed)
        // EUR: earned gross 6000, paid 1000, owed 5000.
        assertEquals(6000L, totals.getValue("EUR").earned)
        assertEquals(5000L, totals.getValue("EUR").owed)
    }

    @Test
    fun `outstandingByCurrency counts only positive balances and groups per currency`() {
        val balances = listOf(
            ClerkProjectBalance("pUsd", "c1", earned = 9000, extras = 0, paid = 4000), // owed 5000
            ClerkProjectBalance("pUsd", "c2", earned = 3000, extras = 0, paid = 1000), // owed 2000
            ClerkProjectBalance("pUsd", "c3", earned = 1000, extras = 0, paid = 3000), // advance -2000 (excluded)
            ClerkProjectBalance("pEur", "c1", earned = 6000, extras = 0, paid = 1000), // owed 5000
        )
        val outstanding = BalanceCalculator.outstandingByCurrency(
            balances,
            currencyByProject = mapOf("pUsd" to "USD", "pEur" to "EUR"),
        )

        // Two currencies, USD first (largest amount), advance never nets it down.
        assertEquals(2, outstanding.size)
        assertEquals("USD", outstanding[0].currency)
        assertEquals(7000L, outstanding[0].amount) // 5000 + 2000, the -2000 advance ignored
        assertEquals("EUR", outstanding[1].currency)
        assertEquals(5000L, outstanding[1].amount)
    }

    @Test
    fun `outstandingByCurrency is empty when nobody is owed`() {
        val balances = listOf(ClerkProjectBalance("p", "c", earned = 1000, extras = 0, paid = 1000))
        assertTrue(BalanceCalculator.outstandingByCurrency(balances, mapOf("p" to "USD")).isEmpty())
    }
}

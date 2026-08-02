package com.vague.crewtally.balance

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkProjectAmount
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.PaymentEntity

/**
 * The derived balance of one (project, clerk) pair. A balance is NEVER stored (LOCKED data
 * model) — it is always recomputed from its three components: [earned] (Σ present × rateSnapshot),
 * [extras] (Σ signed extra-pay lines), and [paid] (Σ payments). All values are MINOR units.
 */
data class ClerkProjectBalance(
    val projectId: String,
    val clerkId: String,
    val earned: Long,
    val extras: Long,
    val paid: Long,
) {
    /** owed = Σ(present × rate) + Σ(extras signed) − Σ(payments). Negative reads as an advance. */
    val owed: Long get() = earned + extras - paid

    /** Gross earnings before payments (days + extras) — the profile's "earned" figure. */
    val grossEarned: Long get() = earned + extras
}

/** How an owed figure should read: a debt, fully settled, or an overpayment (advance). */
enum class OwedStatus { OWED, SETTLED, ADVANCE }

/**
 * Per-currency roll-up of a clerk's activity for the profile (earned / paid / owed). Cross-
 * currency amounts are NEVER summed (LOCKED) — each currency gets its own row.
 */
data class CurrencyTotal(val currency: String, val earned: Long, val paid: Long, val owed: Long)

/** Per-currency outstanding total for the Home dashboard (positive balances only). */
data class OutstandingTotal(val currency: String, val amount: Long)

/**
 * The heart of the app: the pure, storage-free money math. Every function here is a pure
 * function of its inputs so the balance rules are exhaustively unit-testable without Room. The
 * balance screen feeds it raw entity lists; the Home dashboard and clerk profile feed it the
 * SQL component roll-ups via [rollup]. Nothing here — and nothing that calls here — stores a
 * balance.
 */
object BalanceCalculator {

    /** Σ of the daily rate snapshot over PRESENT attendance rows (absent rows earn nothing). */
    fun earnedFrom(attendance: List<AttendanceEntryEntity>): Long =
        attendance.sumOf { if (it.present) it.rateSnapshot else 0L }

    /** Σ of signed extra-pay amounts (deductions are negative). */
    fun extrasFrom(lines: List<ExtraPayLineEntity>): Long = lines.sumOf { it.amount }

    /** Σ of payments made to the clerk. */
    fun paidFrom(payments: List<PaymentEntity>): Long = payments.sumOf { it.amount }

    /** owed = earned + extras − paid. */
    fun owed(earned: Long, extras: Long, paid: Long): Long = earned + extras - paid

    /** Classifies an owed figure for display (a debt, settled, or an advance). */
    fun statusOf(owed: Long): OwedStatus = when {
        owed > 0 -> OwedStatus.OWED
        owed < 0 -> OwedStatus.ADVANCE
        else -> OwedStatus.SETTLED
    }

    /**
     * Whether recording [amount] against a balance that owes [owedBeforeThisPayment] overpays,
     * i.e. produces an advance. For a brand-new payment [owedBeforeThisPayment] is the current
     * owed; for an edit it is the owed with the payment being edited added back in.
     */
    fun isAdvance(amount: Long, owedBeforeThisPayment: Long): Boolean = amount > owedBeforeThisPayment

    /**
     * The owed figure after a payment changes from [oldAmount] to [newAmount] (delete = newAmount 0).
     * Increasing what's been paid lowers what's owed, so owed moves by (oldAmount − newAmount).
     */
    fun owedAfterChange(currentOwed: Long, oldAmount: Long, newAmount: Long): Long =
        currentOwed + (oldAmount - newAmount)

    /**
     * Folds the three per-(project, clerk) component roll-ups into one balance per pair. A pair
     * present in only one roll-up (e.g. a carrier row with extras but no present days) still
     * appears, with the missing components defaulting to zero.
     */
    fun rollup(
        earnings: List<ClerkProjectAmount>,
        extras: List<ClerkProjectAmount>,
        payments: List<ClerkProjectAmount>,
    ): List<ClerkProjectBalance> {
        val earnedBy = earnings.associate { (it.projectId to it.clerkId) to it.amount }
        val extrasBy = extras.associate { (it.projectId to it.clerkId) to it.amount }
        val paidBy = payments.associate { (it.projectId to it.clerkId) to it.amount }
        val keys = earnedBy.keys + extrasBy.keys + paidBy.keys
        return keys.map { key ->
            val (projectId, clerkId) = key
            ClerkProjectBalance(
                projectId = projectId,
                clerkId = clerkId,
                earned = earnedBy[key] ?: 0L,
                extras = extrasBy[key] ?: 0L,
                paid = paidBy[key] ?: 0L,
            )
        }
    }

    /**
     * Groups balances by their project's currency for the clerk profile totals. "earned" here
     * is gross (days + extras) so that earned − paid = owed reads consistently. Currencies are
     * never merged; a project with no known currency is dropped rather than lumped in.
     */
    fun currencyTotals(
        balances: List<ClerkProjectBalance>,
        currencyByProject: Map<String, String>,
    ): List<CurrencyTotal> =
        balances.groupBy { currencyByProject[it.projectId].orEmpty() }
            .filterKeys { it.isNotEmpty() }
            .map { (currency, group) ->
                CurrencyTotal(
                    currency = currency,
                    earned = group.sumOf { it.grossEarned },
                    paid = group.sumOf { it.paid },
                    owed = group.sumOf { it.owed },
                )
            }
            .sortedBy { it.currency }

    /**
     * The Home dashboard's outstanding total per currency: the sum of POSITIVE owed balances
     * only (what still needs paying out). Advances (negative balances) are money the clerk
     * carries, not cash owed, so they do not net down the outstanding figure.
     */
    fun outstandingByCurrency(
        balances: List<ClerkProjectBalance>,
        currencyByProject: Map<String, String>,
    ): List<OutstandingTotal> =
        balances.filter { it.owed > 0 }
            .groupBy { currencyByProject[it.projectId].orEmpty() }
            .filterKeys { it.isNotEmpty() }
            .map { (currency, group) -> OutstandingTotal(currency, group.sumOf { it.owed }) }
            .sortedByDescending { it.amount }
}

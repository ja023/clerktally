package com.vague.crewtally.report

import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity

/**
 * Builds a [ClerkStatement] from the same raw ledger rows the clerk balance screen already
 * observes (attendance entries, dated extra-pay lines, payments), reusing
 * [BalanceCalculator] for every total so the statement's numbers can never drift from the
 * balance screen's. Pure Kotlin, no Room/Android — fully unit-testable.
 */
object ClerkStatementBuilder {

    fun build(
        clerkName: String,
        projectName: String,
        companyName: String,
        currency: String,
        attendance: List<AttendanceEntryEntity>,
        extras: List<ExtraPayLineWithDate>,
        payments: List<PaymentEntity>,
    ): ClerkStatement {
        val dayLines = attendance
            .sortedBy { it.date }
            .map { ClerkStatementDayLine(date = it.date, present = it.present, rateSnapshot = it.rateSnapshot) }
        val extraLines = extras
            .sortedBy { it.date }
            .map { ClerkStatementExtraLine(date = it.date, label = it.line.label, amount = it.line.amount) }
        val paymentLines = payments
            .sortedBy { it.date }
            .map { ClerkStatementPaymentLine(date = it.date, amount = it.amount, note = it.note) }

        val allDates = dayLines.map { it.date } + extraLines.map { it.date } + paymentLines.map { it.date }

        return ClerkStatement(
            clerkName = clerkName,
            projectName = projectName,
            companyName = companyName,
            currency = currency,
            rangeStart = allDates.minOrNull(),
            rangeEnd = allDates.maxOrNull(),
            dayLines = dayLines,
            extraLines = extraLines,
            paymentLines = paymentLines,
            earned = BalanceCalculator.earnedFrom(attendance),
            extras = BalanceCalculator.extrasFrom(extras.map { it.line }),
            paid = BalanceCalculator.paidFrom(payments),
        )
    }
}

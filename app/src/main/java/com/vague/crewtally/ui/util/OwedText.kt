package com.vague.crewtally.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.balance.BalanceCalculator
import com.vague.crewtally.balance.OwedStatus
import com.vague.crewtally.util.Money

/**
 * The one shared phrasing for a derived owed figure, reused by the balance hero, the profile
 * rows, and the payment confirm dialogs so the same balance never reads three different ways:
 * a positive balance is "<amount> owed", a negative balance is "Advance of <amount>" (never a
 * red error — LOCKED #7), and exactly zero is "Paid in full".
 */
@Composable
fun owedDisplayText(owed: Long, symbol: String): String = when (BalanceCalculator.statusOf(owed)) {
    OwedStatus.OWED -> stringResource(R.string.owed_phrase_owed, Money.formatWithSymbol(owed, symbol))
    OwedStatus.ADVANCE -> stringResource(R.string.owed_phrase_advance, Money.formatWithSymbol(-owed, symbol))
    OwedStatus.SETTLED -> stringResource(R.string.owed_phrase_settled)
}

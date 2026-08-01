package com.vague.crewtally.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vague.crewtally.R
import com.vague.crewtally.util.Money
import kotlin.math.abs

/**
 * The accessible reading for a signed money amount. [Money.formatSignedWithSymbol]'s visible
 * minus glyph ("-$15.50") is not reliably spoken by TalkBack — it is instead announced as the
 * word "Deduction" ahead of the magnitude, matching the visible "Deduction" wording used
 * elsewhere for negative extra-pay lines. Four attendance surfaces render a signed extras
 * amount (line rows, the running total, the clerk-row extras chip, and the history summary);
 * this is their one shared rule instead of four hand-rolled copies.
 */
@Composable
fun accessibleMoneyDescription(minorUnits: Long, symbol: String): String {
    val magnitude = if (minorUnits == Long.MIN_VALUE) minorUnits else abs(minorUnits)
    val magnitudeText = Money.formatWithSymbol(magnitude, symbol)
    return if (minorUnits < 0) {
        stringResource(R.string.money_deduction_description, magnitudeText)
    } else {
        magnitudeText
    }
}

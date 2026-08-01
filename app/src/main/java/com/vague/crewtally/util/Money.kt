package com.vague.crewtally.util

import java.math.BigDecimal

/**
 * Conversion between the minor-unit [Long] every money column stores (LOCKED data model —
 * money is never a floating-point value) and the plain decimal text a rate/amount field
 * shows and accepts. Currency is cosmetic only (LOCKED decision #2, zero conversion math),
 * so every currency is treated as having 2 minor-unit places for storage purposes — the
 * same "cents" convention used across the schema regardless of which ISO code a project uses.
 */
object Money {
    private const val MINOR_UNITS_PER_MAJOR = 100L

    /**
     * The largest amount any rate/amount field will accept, in minor units (one billion
     * major units). Nothing in a crew-payroll app legitimately approaches this — it exists
     * to stop a mistyped digit from producing a [Long] that overflows or corrupts downstream
     * balance arithmetic.
     */
    const val MAX_MINOR_UNITS = 1_000_000_000_00L

    /**
     * Parses a user-typed amount (e.g. "45.5", "45.50", "45") into minor units. Returns null
     * for blank, unparsable, negative, more-than-2-decimal-place, or over-ceiling input —
     * callers treat null as "invalid, block save" (the field's supporting text already says
     * enter a valid rate, so no separate error copy is needed per rejection reason).
     *
     * Goes through [BigDecimal] rather than [Double] — a binary float cannot represent every
     * decimal exactly (e.g. 0.1), so multiplying by 100 and rounding can silently land one
     * minor unit off for amounts that look exact to the user. [BigDecimal] parses the typed
     * digits exactly and [BigDecimal.movePointRight] is an exact decimal-place shift.
     */
    fun parseToMinorUnits(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val amount = try {
            BigDecimal(trimmed)
        } catch (e: NumberFormatException) {
            return null
        }
        if (amount.signum() < 0) return null
        if (amount.scale() > 2) return null
        val minorUnits = try {
            amount.movePointRight(2).longValueExact()
        } catch (e: ArithmeticException) {
            return null
        }
        if (minorUnits > MAX_MINOR_UNITS) return null
        return minorUnits
    }

    /**
     * Formats minor units back into plain decimal text for an editable field, e.g. 4550 ->
     * "45.50". Forces [Locale.ROOT] — without it, a device set to a locale with non-ASCII
     * digits (e.g. Arabic) would pre-fill a rate field with text [parseToMinorUnits] can't
     * read back, silently breaking the round trip on save.
     */
    fun formatForInput(minorUnits: Long): String {
        val major = minorUnits / MINOR_UNITS_PER_MAJOR
        val minor = minorUnits % MINOR_UNITS_PER_MAJOR
        return String.format(java.util.Locale.ROOT, "%d.%02d", major, minor)
    }

    /** Formats minor units with a leading currency symbol/code for display, e.g. "$45.50". */
    fun formatWithSymbol(minorUnits: Long, symbol: String): String =
        "$symbol${formatForInput(minorUnits)}"

    /**
     * Formats a SIGNED amount with the sign OUTSIDE the currency symbol, e.g. -1550 -> "-$15.50"
     * and 1550 -> "$15.50". Extra-pay lines and their totals may be negative (deductions), and
     * [formatForInput]'s `%d.%02d` split would render a negative as "-15.-50"; this pulls the
     * sign out and formats the magnitude, so a deduction reads cleanly.
     */
    fun formatSignedWithSymbol(minorUnits: Long, symbol: String): String {
        val sign = if (minorUnits < 0) "-" else ""
        val magnitude = if (minorUnits == Long.MIN_VALUE) minorUnits else kotlin.math.abs(minorUnits)
        return "$sign$symbol${formatForInput(magnitude)}"
    }
}

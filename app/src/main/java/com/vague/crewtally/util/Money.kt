package com.vague.crewtally.util

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
     * Parses a user-typed amount (e.g. "45.5", "45.50", "45") into minor units. Returns null
     * for blank, unparsable, or negative input — callers treat null as "invalid, block save".
     */
    fun parseToMinorUnits(input: String): Long? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val amount = trimmed.toDoubleOrNull() ?: return null
        if (amount < 0.0) return null
        return Math.round(amount * MINOR_UNITS_PER_MAJOR)
    }

    /** Formats minor units back into plain decimal text for an editable field, e.g. 4550 -> "45.50". */
    fun formatForInput(minorUnits: Long): String {
        val major = minorUnits / MINOR_UNITS_PER_MAJOR
        val minor = minorUnits % MINOR_UNITS_PER_MAJOR
        return "%d.%02d".format(major, minor)
    }

    /** Formats minor units with a leading currency symbol/code for display, e.g. "$45.50". */
    fun formatWithSymbol(minorUnits: Long, symbol: String): String =
        "$symbol${formatForInput(minorUnits)}"
}

package com.vague.crewtally.util

/**
 * A curated shortlist of common ISO 4217 currency codes for the project currency picker.
 * Currency is cosmetic only (LOCKED decision #2) — this list exists purely to save typing;
 * the picker also accepts free-text entry of any other ISO code (LOCKED Phase 2 decision).
 */
data class CurrencyOption(val code: String, val symbol: String)

object CurrencyCodes {
    /** ISO 4217 codes are three uppercase letters — the shape free-typed entry must match. */
    private val CODE_PATTERN = Regex("^[A-Z]{3}$")

    val COMMON: List<CurrencyOption> = listOf(
        CurrencyOption("USD", "$"),
        CurrencyOption("LBP", "L£"),
        CurrencyOption("EUR", "€"),
        CurrencyOption("GBP", "£"),
        CurrencyOption("AED", "AED "),
        CurrencyOption("SAR", "SAR "),
        CurrencyOption("EGP", "E£"),
        CurrencyOption("TRY", "₺"),
        CurrencyOption("CAD", "CA$"),
        CurrencyOption("AUD", "AU$"),
    )

    /** The display symbol for [code], falling back to the code itself with a trailing space. */
    fun symbolFor(code: String): String =
        COMMON.find { it.code.equals(code, ignoreCase = true) }?.symbol ?: "$code "

    /** Whether [code] (expected already trimmed + uppercased) is a well-formed ISO 4217 code. */
    fun isValidCode(code: String): Boolean = CODE_PATTERN.matches(code)
}

package com.vague.crewtally.util

import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

/**
 * [Money] is the only place minor-unit [Long] amounts ever meet user-typed decimal text
 * (LOCKED data model — money is never a floating-point value), so its round trip and its
 * rejection rules for malformed input get direct coverage here.
 */
class MoneyTest {

    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `formatting then parsing round-trips to the same minor units`() {
        val minorUnits = 4550L

        val formatted = Money.formatForInput(minorUnits)
        val parsed = Money.parseToMinorUnits(formatted)

        assertEquals("45.50", formatted)
        assertEquals(minorUnits, parsed)
    }

    @Test
    fun `three decimal places is rejected`() {
        assertNull(Money.parseToMinorUnits("1.005"))
    }

    @Test
    fun `one decimal place is scaled to two minor units`() {
        assertEquals(10050L, Money.parseToMinorUnits("100.5"))
    }

    @Test
    fun `negative amount is rejected`() {
        assertNull(Money.parseToMinorUnits("-5.00"))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(Money.parseToMinorUnits(""))
        assertNull(Money.parseToMinorUnits("   "))
    }

    @Test
    fun `garbage input is rejected`() {
        assertNull(Money.parseToMinorUnits("not a number"))
        assertNull(Money.parseToMinorUnits("45.5.0"))
    }

    @Test
    fun `amount over the ceiling is rejected`() {
        assertNull(Money.parseToMinorUnits("1000000001"))
    }

    @Test
    fun `amount at the ceiling is accepted`() {
        assertEquals(Money.MAX_MINOR_UNITS, Money.parseToMinorUnits("1000000000"))
    }

    @Test
    fun `formatted rate under a forced Arabic locale still parses back`() {
        Locale.setDefault(Locale.forLanguageTag("ar-EG"))

        val formatted = Money.formatForInput(4550L)
        val parsed = Money.parseToMinorUnits(formatted)

        assertEquals("45.50", formatted)
        assertEquals(4550L, parsed)
    }
}

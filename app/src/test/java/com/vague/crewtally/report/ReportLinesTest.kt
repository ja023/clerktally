package com.vague.crewtally.report

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.ExtraPayLineWithDate
import com.vague.crewtally.data.local.PaymentEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Structural + phrasing assertions on [ReportLines]' output — the shared content both
 * [TextReportRenderer] and the PDF renderer draw from. Covers the WhatsApp-clean line shape,
 * deduction rendering, the three owed/advance/paid-in-full phrasings, and (v1.1) the project
 * statement's literal column header plus the company statement's dated activity lines and
 * range header.
 */
class ReportLinesTest {

    private val strings = testReportStrings()
    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan10 = LocalDate.of(2026, 1, 10)

    @Test
    fun `clerk statement lines lead with app name and clerk identity`() {
        val statement = ClerkStatementBuilder.build("Ali", "Warehouse Count", "Acme", "USD", emptyList(), emptyList(), emptyList())

        val lines = ReportLines.forClerkStatement(statement, strings)

        assertEquals("CrewTally - Clerk Statement", lines[0])
        assertEquals("Ali - Acme - Warehouse Count", lines[1])
    }

    @Test
    fun `no line in a clerk statement exceeds roughly 40 characters except long free-text content`() {
        val attendance = listOf(AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 5000))
        val payments = listOf(PaymentEntity("pay1", "p", "c", jan10, 2000, "Short note"))
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), payments)

        val lines = ReportLines.forClerkStatement(statement, strings)
        val overlong = lines.filter { it.length > 40 }

        // Every over-length line is one that legitimately carries a free-text payment note or
        // clerk/company/project names strung together — never a structural table row.
        assertTrue(overlong.all { it.contains("Ali") || it.contains("Short note") })
    }

    @Test
    fun `an owed balance renders the owed phrase with the money amount`() {
        val attendance = listOf(AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 13000))
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), emptyList())

        val lines = ReportLines.forClerkStatement(statement, strings)

        assertTrue(lines.last().endsWith("$130.00 owed"))
    }

    @Test
    fun `an advance balance renders as Advance of X, never a negative number`() {
        val attendance = listOf(AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 5000))
        val payments = listOf(PaymentEntity("pay1", "p", "c", jan10, 8000, ""))
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), payments)

        val lines = ReportLines.forClerkStatement(statement, strings)

        assertTrue(lines.last().endsWith("Advance of $30.00"))
        assertTrue(lines.none { it.contains("-$30.00") })
    }

    @Test
    fun `a fully settled balance renders as Paid in full`() {
        val attendance = listOf(AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 5000))
        val payments = listOf(PaymentEntity("pay1", "p", "c", jan10, 5000, ""))
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), payments)

        val lines = ReportLines.forClerkStatement(statement, strings)

        assertTrue(lines.last().endsWith("Paid in full"))
    }

    @Test
    fun `a deduction extra line is labelled Deduction with a minus sign`() {
        val extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Damage", -2000), jan1))
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", emptyList(), extras, emptyList())

        val lines = ReportLines.forClerkStatement(statement, strings)
        val extraLine = lines.single { it.contains("Damage") }

        assertTrue(extraLine.contains("Deduction"))
        assertTrue(extraLine.contains("-$20.00"))
    }

    @Test
    fun `the summary line follows the days x rate plus extras minus paid spirit`() {
        val attendance = listOf(
            AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 2500),
            AttendanceEntryEntity("a2", "p", "c", jan10, present = true, rateSnapshot = 2500),
        )
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), emptyList())

        val summary = ReportLines.forClerkStatement(statement, strings)[4]

        assertEquals("Ali: 2 days x $25.00, extras $0.00, paid $0.00, $50.00 owed", summary)
    }

    @Test
    fun `the summary line drops the per-day rate multiplier when the rate changed mid-project`() {
        val attendance = listOf(
            AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 2500),
            AttendanceEntryEntity("a2", "p", "c", jan10, present = true, rateSnapshot = 3000),
        )
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, emptyList(), emptyList())

        val summary = ReportLines.forClerkStatement(statement, strings)[4]

        assertEquals("Ali: 2 days, earned $55.00, extras $0.00, paid $0.00, $55.00 owed", summary)
    }

    @Test
    fun `a negative earned total renders as a leading minus sign, never a garbled split`() {
        // A deduction larger than the day's earnings (LOCKED decision #7: deductions may
        // exceed earnings) drives earned negative — must read "-$5.00", never "$-5.-00".
        val attendance = listOf(AttendanceEntryEntity("a1", "p", "c", jan1, present = true, rateSnapshot = 2000))
        val extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Damage", -7000), jan1))
        val statement = ClerkStatementBuilder.build("Ali", "Project", "Acme", "USD", attendance, extras, emptyList())

        val lines = ReportLines.forClerkStatement(statement, strings)
        val earnedLine = lines.single { it.startsWith("Earned:") }

        assertEquals("Earned: -$50.00", earnedLine)
    }

    @Test
    fun `company statement lines list each project's clerk rows and end with a grand total section`() {
        val ali = ReportClerkLedgerInput(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)),
            extras = emptyList(),
            payments = listOf(PaymentEntity("pay1", "p1", "c1", jan1, 2000, "")),
        )
        val project = CompanyReportProjectInput("p1", "Warehouse Count", "USD", listOf(ali))
        val totals = CompanyTotalsBuilder.build("Acme", listOf(project))

        val lines = ReportLines.forCompanyTotals(totals, strings)

        assertEquals("CrewTally - Company Statement", lines[0])
        assertEquals("Acme", lines[1])
        assertTrue(lines.any { it.contains("Warehouse Count") })
        assertTrue(lines.any { it.contains("Ali") && it.contains("$30.00 owed") })
        assertTrue(lines.last { it.isNotBlank() }.contains("USD"))
    }

    @Test
    fun `company statement header states the selected range explicitly`() {
        val allTime = CompanyTotalsBuilder.build("Acme", emptyList())
        val bounded = CompanyTotalsBuilder.build("Acme", emptyList(), ReportDateRange(jan1, jan10))

        assertEquals(strings.allTimeLabel, ReportLines.forCompanyTotals(allTime, strings)[2])
        assertEquals("Jan 1, 2026 - Jan 10, 2026", ReportLines.forCompanyTotals(bounded, strings)[2])
    }

    @Test
    fun `company statement activity lines render a present day, a signed extra, and a negative payment`() {
        val ali = ReportClerkLedgerInput(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 2500)),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Bonus", -500), jan1)),
            payments = listOf(PaymentEntity("pay1", "p1", "c1", jan10, 1000, "part payment")),
        )
        val project = CompanyReportProjectInput("p1", "Warehouse Count", "USD", listOf(ali))
        val totals = CompanyTotalsBuilder.build("Acme", listOf(project))

        val lines = ReportLines.forCompanyTotals(totals, strings)

        val dayLine = lines.single { it.contains("Ali - day") }
        assertTrue(dayLine.contains("$25.00"))

        val extraLine = lines.single { it.contains("Ali - Bonus") }
        assertTrue(extraLine.contains("-$5.00"))

        val paymentLine = lines.single { it.contains("Payment -> Ali") }
        assertTrue(paymentLine.contains("-$10.00"))
        assertTrue(paymentLine.contains("part payment"))
    }

    @Test
    fun `company statement renders a negative clerk earned total with a leading minus sign`() {
        // Same negative-earned scenario (deduction exceeds the day's earnings) surfaced in
        // both the per-clerk row and the project subtotal line.
        val ali = ReportClerkLedgerInput(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 2000)),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Damage", -7000), jan1)),
        )
        val project = CompanyReportProjectInput("p1", "Warehouse Count", "USD", listOf(ali))
        val totals = CompanyTotalsBuilder.build("Acme", listOf(project))

        val lines = ReportLines.forCompanyTotals(totals, strings)

        assertTrue(lines.any { it.contains("Ali") && it.contains("earned -$50.00") })
        assertTrue(lines.any { it.startsWith(" Earned -$50.00") })
    }

    @Test
    fun `an empty company statement states none recorded rather than an empty body`() {
        val totals = CompanyTotalsBuilder.build("Acme", emptyList())

        val lines = ReportLines.forCompanyTotals(totals, strings)

        assertTrue(lines.contains(strings.noneRecordedLabel))
    }

    @Test
    fun `project statement lines lead with the literal LOCKED column header and a PROJECT TOTAL line`() {
        val ali = ReportClerkLedgerInput(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000)),
        )
        val statement = ProjectStatementBuilder.build("Warehouse Count", "Acme", "USD", listOf(ali))

        val lines = ReportLines.forProjectStatement(statement, strings)

        assertEquals("CrewTally - Project Statement", lines[0])
        assertEquals("Warehouse Count - Acme", lines[1])
        assertTrue(lines.any { it == strings.projectStatementHeaderLabel })
        assertTrue(lines.any { it.contains("Ali") && it.contains("1") && it.contains("$50.00") })
        assertTrue(lines.any { it == strings.projectTotalLabel })
    }

    @Test
    fun `project statement renders a negative clerk earned row and PROJECT TOTAL with a leading minus sign`() {
        // Same negative-earned scenario (deduction exceeds the day's earnings), asserted on
        // both the per-clerk row and the PROJECT TOTAL line.
        val ali = ReportClerkLedgerInput(
            "c1",
            "Ali",
            attendance = listOf(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 2000)),
            extras = listOf(ExtraPayLineWithDate(ExtraPayLineEntity("x1", "a1", "Damage", -7000), jan1)),
        )
        val statement = ProjectStatementBuilder.build("Warehouse Count", "Acme", "USD", listOf(ali))

        val lines = ReportLines.forProjectStatement(statement, strings)

        assertTrue(lines.any { it.contains("Ali") && it.contains("-$50.00") })
        assertTrue(lines.any { it.startsWith(" ${strings.earnedLabel}: -$50.00") })
    }

    @Test
    fun `project statement header states All time when no range is selected`() {
        val statement = ProjectStatementBuilder.build("Project", "Acme", "USD", emptyList())

        val lines = ReportLines.forProjectStatement(statement, strings)

        assertTrue(lines[2].contains(strings.allTimeLabel))
    }

    @Test
    fun `an empty project statement states none recorded in its clerk table`() {
        val statement = ProjectStatementBuilder.build("Project", "Acme", "USD", emptyList())

        val lines = ReportLines.forProjectStatement(statement, strings)

        assertTrue(lines.any { it.contains(strings.noneRecordedLabel) })
    }
}

/** A hand-built English [ReportStrings] mirroring `strings.xml`, for assertions without Android resources. */
fun testReportStrings(): ReportStrings = ReportStrings(
    appName = "CrewTally",
    clerkStatementTitle = "Clerk Statement",
    companyReportTitle = "Company Statement",
    daysWorkedLabel = "Days worked",
    extrasLabel = "Extras",
    paymentsLabel = "Payments",
    earnedLabel = "Earned",
    paidLabel = "Paid",
    owedLabel = "Owed",
    deductionLabel = "Deduction",
    presentLabel = "Present",
    absentLabel = "Absent",
    noneRecordedLabel = "None recorded",
    projectLabel = "Project",
    grandTotalLabel = "Grand total",
    owedPhraseOwedTemplate = "%1\$s owed",
    owedPhraseAdvanceTemplate = "Advance of %1\$s",
    owedPhraseSettled = "Paid in full",
    summaryLineTemplate = "%1\$s: %2\$d days x %3\$s, extras %4\$s, paid %5\$s, %6\$s",
    summaryLineVaryingRateTemplate = "%1\$s: %2\$d days, earned %3\$s, extras %4\$s, paid %5\$s, %6\$s",
    pageLabelTemplate = "Page %1\$d of %2\$d",
    projectStatementTitle = "Project Statement",
    projectStatementHeaderLabel = "CLERK / DAYS / EARNED / PAID / OWED",
    projectTotalLabel = "PROJECT TOTAL",
    allTimeLabel = "All time",
    dayActivityLabel = "day",
    paymentArrowTemplate = "Payment -> %1\$s",
)

package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeAttendanceWriter
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakeRosterEntryDao
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless JVM tests for [AttendanceExtrasViewModel]: extras may attach to any status, so
 * adding the first line to an Unmarked or Absent clerk must lazily create a present=false
 * carrier row; the Deduction toggle negates the amount; and the carrier for an already-marked
 * clerk must reuse the existing row (never flip its present flag).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceExtrasViewModelTest {

    private val projectId = "project-1"
    private val clerkId = "clerk-a"
    private val date = LocalDate.of(2026, 1, 15)
    private val rateSnapshot = 5000L

    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var writer: FakeAttendanceWriter
    private lateinit var viewModel: AttendanceExtrasViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        rosterEntryDao = FakeRosterEntryDao()
        writer = FakeAttendanceWriter(attendanceEntryDao, extraPayLineDao, rosterEntryDao)
        viewModel = buildViewModel()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = AttendanceExtrasViewModel(
        projectId = projectId,
        clerkId = clerkId,
        date = date,
        rateSnapshot = rateSnapshot,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        attendanceWriter = writer,
    )

    private fun clerkRows() = attendanceEntryDao.all().filter { it.clerkId == clerkId }

    @Test
    fun `adding an extra to an unmarked clerk creates a present-false carrier row and the line`() = runTest {
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("10.00"))

        viewModel.onEvent(AttendanceExtrasEvent.AddLine)

        val rows = clerkRows()
        assertEquals("a carrier row must be created", 1, rows.size)
        assertFalse("carrier is present=false", rows.single().present)
        assertEquals(rateSnapshot, rows.single().rateSnapshot)

        val lines = extraPayLineDao.all()
        assertEquals(1, lines.size)
        assertEquals(1000L, lines.single().amount)
        assertEquals("Lunch", lines.single().label)
        assertEquals(rows.single().id, lines.single().attendanceEntryId)
    }

    @Test
    fun `the deduction toggle stores a negative amount`() = runTest {
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("5.00"))
        viewModel.onEvent(AttendanceExtrasEvent.DeductionChanged(true))

        viewModel.onEvent(AttendanceExtrasEvent.AddLine)

        assertEquals(-500L, extraPayLineDao.all().single().amount)
    }

    @Test
    fun `adding an extra to an absent clerk reuses the row and keeps present false`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-1", projectId = projectId, clerkId = clerkId, date = date, present = false, rateSnapshot = rateSnapshot,
            ),
        )
        viewModel = buildViewModel()
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("8"))

        viewModel.onEvent(AttendanceExtrasEvent.AddLine)

        val rows = clerkRows()
        assertEquals("must reuse the existing row, not add another", 1, rows.size)
        assertEquals("att-1", rows.single().id)
        assertFalse(rows.single().present)
        assertEquals("att-1", extraPayLineDao.all().single().attendanceEntryId)
    }

    @Test
    fun `adding an extra to a present clerk keeps present true`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-1", projectId = projectId, clerkId = clerkId, date = date, present = true, rateSnapshot = rateSnapshot,
            ),
        )
        viewModel = buildViewModel()
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("8"))

        viewModel.onEvent(AttendanceExtrasEvent.AddLine)

        assertTrue("present flag must be preserved", clerkRows().single().present)
    }

    @Test
    fun `a custom preset uses the typed label`() = runTest {
        viewModel.onEvent(AttendanceExtrasEvent.PresetChanged(ExtraPreset.CUSTOM))
        viewModel.onEvent(AttendanceExtrasEvent.CustomLabelChanged("Tools"))
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("3.50"))

        viewModel.onEvent(AttendanceExtrasEvent.AddLine)

        assertEquals("Tools", extraPayLineDao.all().single().label)
        assertEquals(350L, extraPayLineDao.all().single().amount)
    }

    @Test
    fun `a blank custom label blocks the add and flags the field`() = runTest {
        viewModel.onEvent(AttendanceExtrasEvent.PresetChanged(ExtraPreset.CUSTOM))
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("3.50"))

        viewModel.onEvent(AttendanceExtrasEvent.AddLine)

        assertTrue(viewModel.state.value.customLabelError)
        assertTrue(extraPayLineDao.all().isEmpty())
        assertTrue("no carrier row on a blocked add", clerkRows().isEmpty())
    }

    @Test
    fun `a blank or zero amount blocks the add and flags the field`() = runTest {
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged(""))
        viewModel.onEvent(AttendanceExtrasEvent.AddLine)
        assertTrue(viewModel.state.value.amountError)
        assertTrue(extraPayLineDao.all().isEmpty())

        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("0"))
        viewModel.onEvent(AttendanceExtrasEvent.AddLine)
        assertTrue(viewModel.state.value.amountError)
        assertTrue(extraPayLineDao.all().isEmpty())
        assertTrue(clerkRows().isEmpty())
    }

    @Test
    fun `deleting a line removes it`() = runTest {
        val line = ExtraPayLineEntity(id = "x1", attendanceEntryId = "att-1", label = "Bonus", amount = 2000)
        extraPayLineDao.seed(line)

        viewModel.onEvent(AttendanceExtrasEvent.DeleteLine(line))

        assertTrue(extraPayLineDao.all().isEmpty())
    }

    @Test
    fun `deleting the last extra on a carrier-only row deletes the carrier row too`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-1",
                projectId = projectId,
                clerkId = clerkId,
                date = date,
                present = false,
                rateSnapshot = rateSnapshot,
                explicitlyMarked = false,
            ),
        )
        val line = ExtraPayLineEntity(id = "x1", attendanceEntryId = "att-1", label = "Lunch", amount = 1000)
        extraPayLineDao.seed(line)

        viewModel.onEvent(AttendanceExtrasEvent.DeleteLine(line))

        assertTrue("the line is gone", extraPayLineDao.all().isEmpty())
        assertTrue("the carrier-only row must not masquerade as a real Absent", clerkRows().isEmpty())
    }

    @Test
    fun `deleting the last extra on an explicitly-marked absent row keeps the row`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-1",
                projectId = projectId,
                clerkId = clerkId,
                date = date,
                present = false,
                rateSnapshot = rateSnapshot,
                explicitlyMarked = true,
            ),
        )
        val line = ExtraPayLineEntity(id = "x1", attendanceEntryId = "att-1", label = "Lunch", amount = 1000)
        extraPayLineDao.seed(line)

        viewModel.onEvent(AttendanceExtrasEvent.DeleteLine(line))

        assertTrue("the line is gone", extraPayLineDao.all().isEmpty())
        assertEquals("a real Absent mark never auto-deletes", 1, clerkRows().size)
    }

    @Test
    fun `deleting one of two extras on a carrier-only row keeps the row`() = runTest {
        attendanceEntryDao.seed(
            AttendanceEntryEntity(
                id = "att-1",
                projectId = projectId,
                clerkId = clerkId,
                date = date,
                present = false,
                rateSnapshot = rateSnapshot,
                explicitlyMarked = false,
            ),
        )
        val line1 = ExtraPayLineEntity(id = "x1", attendanceEntryId = "att-1", label = "Lunch", amount = 1000)
        val line2 = ExtraPayLineEntity(id = "x2", attendanceEntryId = "att-1", label = "Transport", amount = 500)
        extraPayLineDao.seed(line1, line2)

        viewModel.onEvent(AttendanceExtrasEvent.DeleteLine(line1))

        assertEquals("only the deleted line is gone", listOf("x2"), extraPayLineDao.all().map { it.id })
        assertEquals("the row still carries a remaining line", 1, clerkRows().size)
    }

    @Test
    fun `the form resets after a successful add`() = runTest {
        viewModel.onEvent(AttendanceExtrasEvent.PresetChanged(ExtraPreset.BONUS))
        viewModel.onEvent(AttendanceExtrasEvent.AmountChanged("12"))
        viewModel.onEvent(AttendanceExtrasEvent.DeductionChanged(true))

        viewModel.onEvent(AttendanceExtrasEvent.AddLine)

        val state = viewModel.state.value
        assertEquals("", state.amountInput)
        assertEquals("", state.customLabel)
        assertFalse(state.isDeduction)
        assertEquals(ExtraPreset.LUNCH, state.preset)
    }
}

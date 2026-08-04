package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.report.ReportRangePreset
import com.vague.crewtally.report.testReportStrings
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeCompanyDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakePaymentDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeReportFileWriter
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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
 * Headless tests for [ProjectStatementShareViewModel] (NEW v1.1): the generation-failure path
 * (mirroring [ClerkStatementShareViewModelTest]) plus the range selector regenerating the
 * preview when the preset changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectStatementShareViewModelTest {

    private lateinit var projectDao: FakeProjectDao
    private lateinit var companyDao: FakeCompanyDao
    private lateinit var clerkDao: FakeClerkDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var paymentDao: FakePaymentDao
    private lateinit var testDispatcher: TestDispatcher

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val feb1 = LocalDate.of(2026, 2, 1)

    @Before
    fun setUp() {
        testDispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        companyDao = FakeCompanyDao().apply { seed(CompanyEntity(id = "co1", name = "Acme")) }
        projectDao = FakeProjectDao().apply {
            seed(ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD"))
        }
        clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "c1", name = "Ali")) }
        attendanceEntryDao = FakeAttendanceEntryDao().apply {
            seed(
                AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000),
                AttendanceEntryEntity("a2", "p1", "c1", feb1, present = true, rateSnapshot = 5000),
            )
        }
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        paymentDao = FakePaymentDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(fileWriter: FakeReportFileWriter, today: LocalDate = jan1) = ProjectStatementShareViewModel(
        projectId = "p1",
        projectDao = projectDao,
        companyDao = companyDao,
        clerkDao = clerkDao,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        paymentDao = paymentDao,
        fileWriter = fileWriter,
        strings = testReportStrings(),
        today = today,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `a write failure resets isGenerating and surfaces generationFailure`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter(shouldFail = true))
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }

        viewModel.onEvent(ProjectStatementShareEvent.ShareAsText)
        advanceUntilIdle()

        assertFalse(latest.isGenerating)
        assertTrue(latest.generationFailure)

        job.cancel()
    }

    @Test
    fun `dismissing the generation failure clears it`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter(shouldFail = true))
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }

        viewModel.onEvent(ProjectStatementShareEvent.ShareAsText)
        advanceUntilIdle()
        assertTrue(latest.generationFailure)

        viewModel.onEvent(ProjectStatementShareEvent.DismissGenerationFailure)
        assertFalse(latest.generationFailure)

        job.cancel()
    }

    @Test
    fun `a successful write leaves isGenerating false with no failure`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter(shouldFail = false))
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }

        viewModel.onEvent(ProjectStatementShareEvent.ShareAsText)
        advanceUntilIdle()

        assertFalse(latest.isGenerating)
        assertFalse(latest.generationFailure)

        job.cancel()
    }

    @Test
    fun `default range is All time and includes activity from every month`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter())
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        assertEquals(ReportRangePreset.ALL_TIME, latest.rangePreset)
        assertEquals(2, latest.statement?.clerkRows?.single()?.daysWorked)

        job.cancel()
    }

    @Test
    fun `switching to This month regenerates the preview scoped to today's month`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter(), today = jan1)
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ProjectStatementShareEvent.RangePresetChanged(ReportRangePreset.THIS_MONTH))
        advanceUntilIdle()

        // Only the Jan 1 attendance row falls inside January (today's month); the Feb 1 row drops out.
        assertEquals(1, latest.statement?.clerkRows?.single()?.daysWorked)

        job.cancel()
    }

    @Test
    fun `selecting CUSTOM and setting both dates scopes the statement to that window`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter())
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ProjectStatementShareEvent.RangePresetChanged(ReportRangePreset.CUSTOM))
        viewModel.onEvent(ProjectStatementShareEvent.CustomStartChanged(jan1))
        viewModel.onEvent(ProjectStatementShareEvent.CustomEndChanged(jan1))
        advanceUntilIdle()

        // Only the Jan 1 attendance row falls inside the custom window; the Feb 1 row drops out.
        assertEquals(1, latest.statement?.clerkRows?.single()?.daysWorked)
        assertEquals(jan1, latest.customStart)
        assertEquals(jan1, latest.customEnd)
        assertFalse(latest.isRangeInvalid)

        job.cancel()
    }

    @Test
    fun `selecting CUSTOM without touching dates defaults both to today, matching what is applied`() = runTest {
        // Regression for the bug where displayed fields fell back to today for display only
        // while null/null state silently applied All time underneath.
        val viewModel = buildViewModel(FakeReportFileWriter(), today = jan1)
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ProjectStatementShareEvent.RangePresetChanged(ReportRangePreset.CUSTOM))
        advanceUntilIdle()

        assertEquals(jan1, latest.customStart)
        assertEquals(jan1, latest.customEnd)
        assertFalse(latest.isRangeInvalid)
        // Applied range is jan1..jan1 (today), matching the displayed dates — only the Jan 1
        // attendance row is in range, proving All time was NOT silently applied instead.
        assertEquals(1, latest.statement?.clerkRows?.single()?.daysWorked)

        job.cancel()
    }

    @Test
    fun `an inverted custom range exposes isRangeInvalid and builds no statement`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter())
        var latest = ProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ProjectStatementShareEvent.RangePresetChanged(ReportRangePreset.CUSTOM))
        viewModel.onEvent(ProjectStatementShareEvent.CustomStartChanged(feb1))
        viewModel.onEvent(ProjectStatementShareEvent.CustomEndChanged(jan1))
        advanceUntilIdle()

        assertTrue(latest.isRangeInvalid)
        assertEquals(null, latest.statement)
        // The project identity itself is still loaded; only the statement is withheld.
        assertTrue(latest.isLoaded)

        job.cancel()
    }
}

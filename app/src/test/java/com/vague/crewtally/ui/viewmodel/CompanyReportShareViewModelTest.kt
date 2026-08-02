package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.ProjectEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless tests for [CompanyReportShareViewModel]'s generation-failure path — mirrors
 * [ClerkStatementShareViewModelTest] (see that file's KDoc for the file-writer failure shape).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanyReportShareViewModelTest {

    private lateinit var companyDao: FakeCompanyDao
    private lateinit var projectDao: FakeProjectDao
    private lateinit var clerkDao: FakeClerkDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var paymentDao: FakePaymentDao
    private lateinit var testDispatcher: TestDispatcher

    private val jan1 = LocalDate.of(2026, 1, 1)

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
            seed(AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000))
        }
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        paymentDao = FakePaymentDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(fileWriter: FakeReportFileWriter) = CompanyReportShareViewModel(
        companyId = "co1",
        companyDao = companyDao,
        projectDao = projectDao,
        clerkDao = clerkDao,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        paymentDao = paymentDao,
        fileWriter = fileWriter,
        strings = testReportStrings(),
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `a write failure resets isGenerating and surfaces generationFailure`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter(shouldFail = true))
        var latest = CompanyReportShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }

        viewModel.onEvent(CompanyReportShareEvent.ShareAsPdf)
        advanceUntilIdle()

        assertFalse(latest.isGenerating)
        assertTrue(latest.generationFailure)

        job.cancel()
    }

    @Test
    fun `dismissing the generation failure clears it`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter(shouldFail = true))
        var latest = CompanyReportShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }

        viewModel.onEvent(CompanyReportShareEvent.ShareAsPdf)
        advanceUntilIdle()
        assertTrue(latest.generationFailure)

        viewModel.onEvent(CompanyReportShareEvent.DismissGenerationFailure)
        assertFalse(latest.generationFailure)

        job.cancel()
    }

    @Test
    fun `a successful write leaves isGenerating false with no failure`() = runTest {
        val viewModel = buildViewModel(FakeReportFileWriter(shouldFail = false))
        var latest = CompanyReportShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }

        viewModel.onEvent(CompanyReportShareEvent.ShareAsPdf)
        advanceUntilIdle()

        assertFalse(latest.isGenerating)
        assertFalse(latest.generationFailure)

        job.cancel()
    }
}

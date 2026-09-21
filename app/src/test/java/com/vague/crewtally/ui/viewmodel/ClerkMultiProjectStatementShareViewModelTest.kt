package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.CompanyEntity
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.report.ClerkProjectBucket
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
 * Headless tests for [ClerkMultiProjectStatementShareViewModel] (NEW v1.2): bucket scoping of
 * the two statements, the empty-bucket state that hides the share actions, range filtering, and
 * the shared generation-failure path (see [ClerkStatementShareViewModelTest] for that shape).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClerkMultiProjectStatementShareViewModelTest {

    private lateinit var clerkDao: FakeClerkDao
    private lateinit var companyDao: FakeCompanyDao
    private lateinit var projectDao: FakeProjectDao
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
        clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "c1", name = "Ali")) }
        companyDao = FakeCompanyDao().apply { seed(CompanyEntity(id = "co1", name = "Acme")) }
        projectDao = FakeProjectDao().apply {
            seed(
                ProjectEntity(id = "p1", companyId = "co1", name = "Live site", startDate = jan1, currency = "USD"),
                ProjectEntity(
                    id = "p2",
                    companyId = "co1",
                    name = "Old site",
                    startDate = jan1,
                    status = ProjectStatus.COMPLETED,
                    currency = "USD",
                ),
            )
        }
        attendanceEntryDao = FakeAttendanceEntryDao().apply {
            seed(
                AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000),
                AttendanceEntryEntity("a2", "p2", "c1", jan1, present = true, rateSnapshot = 3000),
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

    private fun buildViewModel(
        bucket: ClerkProjectBucket = ClerkProjectBucket.ACTIVE,
        fileWriter: FakeReportFileWriter = FakeReportFileWriter(),
        today: LocalDate = jan1,
    ) = ClerkMultiProjectStatementShareViewModel(
        clerkId = "c1",
        bucket = bucket,
        clerkDao = clerkDao,
        projectDao = projectDao,
        companyDao = companyDao,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        paymentDao = paymentDao,
        fileWriter = fileWriter,
        strings = testReportStrings(),
        today = today,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `the active statement covers only projects that are neither completed nor archived`() = runTest {
        val viewModel = buildViewModel(ClerkProjectBucket.ACTIVE)
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        assertEquals(listOf("Live site"), latest.statement?.sections?.map { it.statement.projectName })
        assertEquals("Ali", latest.clerkName)
        assertTrue(latest.isLoaded)
        assertTrue(latest.hasContent)

        job.cancel()
    }

    @Test
    fun `the history statement covers completed and archived projects`() = runTest {
        val viewModel = buildViewModel(ClerkProjectBucket.HISTORY)
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        assertEquals(listOf("Old site"), latest.statement?.sections?.map { it.statement.projectName })
        assertEquals(3000L, latest.statement?.grandTotalsByCurrency?.single()?.earned)

        job.cancel()
    }

    @Test
    fun `an empty bucket reports no content and no project count`() = runTest {
        // Only the completed project keeps activity; the active one has none at all.
        attendanceEntryDao.seed(AttendanceEntryEntity("a2", "p2", "c1", jan1, present = true, rateSnapshot = 3000))
        val viewModel = buildViewModel(ClerkProjectBucket.ACTIVE)
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        assertFalse(latest.hasContent)
        assertEquals(0, latest.bucketProjectCount)
        assertTrue(latest.statement?.sections?.isEmpty() == true)

        job.cancel()
    }

    @Test
    fun `an empty bucket shares nothing at all`() = runTest {
        attendanceEntryDao.seed(AttendanceEntryEntity("a2", "p2", "c1", jan1, present = true, rateSnapshot = 3000))
        val viewModel = buildViewModel(ClerkProjectBucket.ACTIVE)
        var shared = 0
        // uiState must be collected too: the statement is a WhileSubscribed StateFlow, exactly
        // as on the live screen, so nothing is built while nobody observes the UI state.
        val state = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { } }
        val shares = launch(Dispatchers.Unconfined) { viewModel.shareRequests.collect { shared++ } }
        advanceUntilIdle()

        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.ShareAsText)
        advanceUntilIdle()

        assertEquals(0, shared)

        shares.cancel()
        state.cancel()
    }

    @Test
    fun `sharing as text emits one share request`() = runTest {
        val viewModel = buildViewModel()
        var shared = 0
        val state = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { } }
        val shares = launch(Dispatchers.Unconfined) { viewModel.shareRequests.collect { shared++ } }
        advanceUntilIdle()

        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.ShareAsText)
        advanceUntilIdle()

        assertEquals(1, shared)

        shares.cancel()
        state.cancel()
    }

    @Test
    fun `a write failure resets isGenerating and surfaces generationFailure`() = runTest {
        val viewModel = buildViewModel(fileWriter = FakeReportFileWriter(shouldFail = true))
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.ShareAsPdf)
        advanceUntilIdle()

        assertFalse(latest.isGenerating)
        assertTrue(latest.generationFailure)

        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.DismissGenerationFailure)
        assertFalse(latest.generationFailure)

        job.cancel()
    }

    @Test
    fun `a custom range scopes the statement and can empty it out`() = runTest {
        // The only active-project day is Jan 1; a February window leaves nothing to share, even
        // though the bucket itself still holds one project.
        val viewModel = buildViewModel()
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.RangePresetChanged(ReportRangePreset.CUSTOM))
        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.CustomStartChanged(feb1))
        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.CustomEndChanged(feb1))
        advanceUntilIdle()

        assertFalse(latest.hasContent)
        assertEquals(1, latest.bucketProjectCount)
        assertFalse(latest.isRangeInvalid)

        job.cancel()
    }

    @Test
    fun `an inverted custom range exposes isRangeInvalid and builds no statement`() = runTest {
        val viewModel = buildViewModel()
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.RangePresetChanged(ReportRangePreset.CUSTOM))
        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.CustomStartChanged(feb1))
        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.CustomEndChanged(jan1))
        advanceUntilIdle()

        assertTrue(latest.isRangeInvalid)
        assertEquals(null, latest.statement)
        // The clerk identity itself is still loaded; only the statement is withheld.
        assertTrue(latest.isLoaded)

        job.cancel()
    }

    @Test
    fun `payments on a bucket project count against that project's total`() = runTest {
        paymentDao.seed(PaymentEntity("pay1", "p1", "c1", jan1, 2000, ""))
        val viewModel = buildViewModel()
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        val total = latest.statement?.grandTotalsByCurrency?.single()
        assertEquals(5000L, total?.earned)
        assertEquals(2000L, total?.paid)
        assertEquals(3000L, total?.owed)

        job.cancel()
    }

    @Test
    fun `selecting CUSTOM without touching dates defaults both to today`() = runTest {
        val viewModel = buildViewModel(today = jan1)
        var latest = ClerkMultiProjectStatementShareUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        advanceUntilIdle()

        viewModel.onEvent(ClerkMultiProjectStatementShareEvent.RangePresetChanged(ReportRangePreset.CUSTOM))
        advanceUntilIdle()

        assertEquals(jan1, latest.customStart)
        assertEquals(jan1, latest.customEnd)
        // jan1..jan1 still admits the seeded Jan 1 day, proving All time was not silently applied.
        assertTrue(latest.hasContent)

        job.cancel()
    }
}

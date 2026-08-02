package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectStatus
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakePaymentDao
import com.vague.crewtally.testutil.FakeProjectDao
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Headless tests for [HomeViewModel]: the active-projects section, the outstanding total per
 * currency (positive only, never summed across currencies), the owed-clerks list sorted by
 * amount, and the empty state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var projectDao: FakeProjectDao
    private lateinit var clerkDao: FakeClerkDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var paymentDao: FakePaymentDao

    private val jan1 = LocalDate.of(2026, 1, 1)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        projectDao = FakeProjectDao()
        clerkDao = FakeClerkDao()
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        paymentDao = FakePaymentDao()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel() = HomeViewModel(
        projectDao = projectDao,
        clerkDao = clerkDao,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        paymentDao = paymentDao,
    )

    private fun seedDashboard() {
        projectDao.companyNames = mapOf("co1" to "Acme")
        projectDao.seed(
            ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD", status = ProjectStatus.ACTIVE),
            ProjectEntity(id = "p2", companyId = "co1", name = "Stocktake", startDate = jan1, currency = "EUR", status = ProjectStatus.COMPLETED),
        )
        clerkDao.seed(ClerkEntity(id = "c1", name = "Ali"), ClerkEntity(id = "c2", name = "Sam"))
        attendanceEntryDao.seed(
            AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000), // USD earned 5000
            AttendanceEntryEntity("a2", "p1", "c2", jan1, present = true, rateSnapshot = 4000), // USD earned 4000
            AttendanceEntryEntity("a3", "p2", "c1", jan1, present = true, rateSnapshot = 6000), // EUR earned 6000
        )
        paymentDao.seed(
            PaymentEntity("pay1", "p1", "c1", jan1, 2000, ""), // c1@p1 owed 3000
            PaymentEntity("pay2", "p1", "c2", jan1, 4000, ""), // c2@p1 owed 0 (settled)
            PaymentEntity("pay3", "p2", "c1", jan1, 1000, ""), // c1@p2 owed 5000
        )
    }

    @Test
    fun `active projects section lists only active projects`() = runTest {
        seedDashboard()
        val vm = buildViewModel()
        var latest = HomeUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        assertEquals(1, latest.activeProjects.size)
        assertEquals("p1", latest.activeProjects.single().project.id)
        assertEquals("Acme", latest.activeProjects.single().companyName)

        job.cancel()
    }

    @Test
    fun `outstanding totals are per currency and never summed across currencies`() = runTest {
        seedDashboard()
        val vm = buildViewModel()
        var latest = HomeUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        // USD outstanding 3000 (c1@p1), EUR 5000 (c1@p2); settled c2 contributes nothing.
        assertEquals(2, latest.outstanding.size)
        val byCurrency = latest.outstanding.associate { it.currency to it.amount }
        assertEquals(3000L, byCurrency["USD"])
        assertEquals(5000L, byCurrency["EUR"])

        job.cancel()
    }

    @Test
    fun `owed clerks are sorted by amount descending and settled clerks are excluded`() = runTest {
        seedDashboard()
        val vm = buildViewModel()
        var latest = HomeUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        // c1@p2 (5000) before c1@p1 (3000); c2 (settled) absent.
        assertEquals(2, latest.owedClerks.size)
        assertEquals(5000L, latest.owedClerks[0].owed)
        assertEquals("p2", latest.owedClerks[0].projectId)
        assertEquals("Ali", latest.owedClerks[0].clerkName)
        assertEquals(3000L, latest.owedClerks[1].owed)

        job.cancel()
    }

    @Test
    fun `empty database yields empty sections but a loaded state`() = runTest {
        val vm = buildViewModel()
        var latest = HomeUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        assertTrue(latest.isLoaded)
        assertTrue(latest.activeProjects.isEmpty())
        assertTrue(latest.outstanding.isEmpty())
        assertTrue(latest.owedClerks.isEmpty())

        job.cancel()
    }
}

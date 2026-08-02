package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectEntity
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
 * Headless tests for [ClerkProfileViewModel]: cross-project totals grouped PER CURRENCY (never
 * summed across currencies), the per-project rows sorted by amount owed, and the empty state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClerkProfileViewModelTest {

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

    private fun buildViewModel(clerkId: String) = ClerkProfileViewModel(
        clerkId = clerkId,
        clerkDao = clerkDao,
        projectDao = projectDao,
        attendanceEntryDao = attendanceEntryDao,
        extraPayLineDao = extraPayLineDao,
        paymentDao = paymentDao,
    )

    private fun seedProfile() {
        projectDao.seed(
            ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD"),
            ProjectEntity(id = "p2", companyId = "co1", name = "Stocktake", startDate = jan1, currency = "EUR"),
        )
        clerkDao.seed(ClerkEntity(id = "c1", name = "Ali"), ClerkEntity(id = "c2", name = "Sam"))
        attendanceEntryDao.seed(
            AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000), // USD earned 5000
            AttendanceEntryEntity("a2", "p2", "c1", jan1, present = true, rateSnapshot = 6000), // EUR earned 6000
            AttendanceEntryEntity("a3", "p1", "c2", jan1, present = true, rateSnapshot = 9000), // other clerk — must be scoped out
        )
        paymentDao.seed(
            PaymentEntity("pay1", "p1", "c1", jan1, 2000, ""), // c1@p1 owed 3000
            PaymentEntity("pay2", "p2", "c1", jan1, 1000, ""), // c1@p2 owed 5000
        )
    }

    @Test
    fun `totals are grouped per currency and never summed across them`() = runTest {
        seedProfile()
        val vm = buildViewModel("c1")
        var latest = ClerkProfileUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        assertEquals("Ali", latest.clerkName)
        val byCurrency = latest.currencyTotals.associateBy { it.currency }
        assertEquals(2, byCurrency.size)
        // USD: earned 5000, paid 2000, owed 3000.
        assertEquals(5000L, byCurrency.getValue("USD").earned)
        assertEquals(2000L, byCurrency.getValue("USD").paid)
        assertEquals(3000L, byCurrency.getValue("USD").owed)
        // EUR: earned 6000, paid 1000, owed 5000.
        assertEquals(6000L, byCurrency.getValue("EUR").earned)
        assertEquals(5000L, byCurrency.getValue("EUR").owed)

        job.cancel()
    }

    @Test
    fun `per-project rows are sorted by owed descending and scoped to the clerk`() = runTest {
        seedProfile()
        val vm = buildViewModel("c1")
        var latest = ClerkProfileUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        assertEquals(2, latest.projectRows.size) // c2's project row must not leak in
        assertEquals("p2", latest.projectRows[0].projectId) // 5000 owed first
        assertEquals(5000L, latest.projectRows[0].owed)
        assertEquals("EUR", latest.projectRows[0].currency)
        assertEquals("p1", latest.projectRows[1].projectId)
        assertEquals(3000L, latest.projectRows[1].owed)

        job.cancel()
    }

    @Test
    fun `a clerk with no activity has empty totals and rows`() = runTest {
        seedProfile()
        clerkDao.seed(
            ClerkEntity(id = "c1", name = "Ali"),
            ClerkEntity(id = "c2", name = "Sam"),
            ClerkEntity(id = "c3", name = "Noor"),
        )
        val vm = buildViewModel("c3")
        var latest = ClerkProfileUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        assertTrue(latest.isLoaded)
        assertEquals("Noor", latest.clerkName)
        assertTrue(latest.currencyTotals.isEmpty())
        assertTrue(latest.projectRows.isEmpty())

        job.cancel()
    }
}

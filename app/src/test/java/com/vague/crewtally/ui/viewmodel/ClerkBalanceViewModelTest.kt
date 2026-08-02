package com.vague.crewtally.ui.viewmodel

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.ExtraPayLineEntity
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakeExtraPayLineDao
import com.vague.crewtally.testutil.FakePaymentDao
import com.vague.crewtally.testutil.FakeProjectDao
import com.vague.crewtally.testutil.FakeRosterEntryDao
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
import org.junit.Before
import org.junit.Test

/**
 * Headless tests for [ClerkBalanceViewModel]: the derived owed figure and ledger breakdown, and
 * that recording a payment updates the owed figure reactively (no navigation, no manual refresh).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClerkBalanceViewModelTest {

    private lateinit var projectDao: FakeProjectDao
    private lateinit var clerkDao: FakeClerkDao
    private lateinit var rosterEntryDao: FakeRosterEntryDao
    private lateinit var attendanceEntryDao: FakeAttendanceEntryDao
    private lateinit var extraPayLineDao: FakeExtraPayLineDao
    private lateinit var paymentDao: FakePaymentDao
    private lateinit var viewModel: ClerkBalanceViewModel

    private val jan1 = LocalDate.of(2026, 1, 1)
    private val jan2 = LocalDate.of(2026, 1, 2)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        projectDao = FakeProjectDao()
        clerkDao = FakeClerkDao()
        rosterEntryDao = FakeRosterEntryDao()
        attendanceEntryDao = FakeAttendanceEntryDao()
        extraPayLineDao = FakeExtraPayLineDao(attendanceEntryDao)
        attendanceEntryDao.extraPayLineDao = extraPayLineDao
        paymentDao = FakePaymentDao()

        projectDao.seed(ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD"))
        clerkDao.seed(ClerkEntity(id = "c1", name = "Ali"))
        attendanceEntryDao.seed(
            AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 5000),
            AttendanceEntryEntity("a2", "p1", "c1", jan2, present = false, rateSnapshot = 5000),
        )
        extraPayLineDao.seed(
            ExtraPayLineEntity("x1", "a1", "Lunch", 1000),
            ExtraPayLineEntity("x2", "a1", "Fine", -300),
        )
        paymentDao.seed(PaymentEntity("pay1", "p1", "c1", LocalDate.of(2026, 1, 3), 4000, ""))

        viewModel = ClerkBalanceViewModel(
            projectId = "p1",
            clerkId = "c1",
            projectDao = projectDao,
            clerkDao = clerkDao,
            rosterEntryDao = rosterEntryDao,
            attendanceEntryDao = attendanceEntryDao,
            extraPayLineDao = extraPayLineDao,
            paymentDao = paymentDao,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `derives owed from earned plus extras minus paid with the right ledger`() = runTest {
        var latest = ClerkBalanceUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }

        assertEquals("Ali", latest.clerkName)
        assertEquals("USD", latest.currency)
        assertEquals(5000L, latest.earned) // only the present day
        assertEquals(700L, latest.extrasTotal) // 1000 - 300
        assertEquals(4000L, latest.paid)
        assertEquals(1700L, latest.owed) // 5000 + 700 - 4000
        assertEquals(1, latest.presentDays.size) // absent day excluded from the day ledger
        assertEquals("a1", latest.presentDays.single().id)
        assertEquals(2, latest.extraLines.size)
        assertEquals(1, latest.payments.size)

        job.cancel()
    }

    @Test
    fun `recording a payment lowers the owed figure reactively`() = runTest {
        var latest = ClerkBalanceUiState()
        val job = launch(Dispatchers.Unconfined) { viewModel.uiState.collect { latest = it } }
        assertEquals(1700L, latest.owed)

        // Settle the rest — as the payment form would, straight into the same DAO flow.
        paymentDao.upsert(PaymentEntity("pay2", "p1", "c1", LocalDate.of(2026, 1, 4), 1700, ""))

        assertEquals(0L, latest.owed)
        assertEquals(5700L, latest.paid)

        job.cancel()
    }
}

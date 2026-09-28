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
 * currency (positive only, never summed across currencies), the owed-clerks groups (one group
 * per currency, sorted by amount owed descending WITHIN each group, group order matching the
 * outstanding totals), and the empty state.
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
    fun `owed clerks are grouped per currency, in the same order as outstanding totals, and settled clerks are excluded`() = runTest {
        seedDashboard()
        val vm = buildViewModel()
        var latest = HomeUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        // Outstanding: EUR 5000 (c1@p2), USD 3000 (c1@p1) -> EUR group first (5000 > 3000).
        assertEquals(2, latest.owedClerkGroups.size)

        val eurGroup = latest.owedClerkGroups[0]
        assertEquals("EUR", eurGroup.currency)
        assertEquals(1, eurGroup.clerks.size)
        assertEquals(5000L, eurGroup.clerks[0].owed)
        assertEquals("p2", eurGroup.clerks[0].projectId)
        assertEquals("Ali", eurGroup.clerks[0].clerkName)

        val usdGroup = latest.owedClerkGroups[1]
        assertEquals("USD", usdGroup.currency)
        assertEquals(1, usdGroup.clerks.size)
        assertEquals(3000L, usdGroup.clerks[0].owed)

        // c2 (settled at p1) never appears in any group.
        assertTrue(latest.owedClerkGroups.none { group -> group.clerks.any { it.clerkId == "c2" } })

        job.cancel()
    }

    @Test
    fun `multiple owed clerks in the same currency are sorted by amount descending within their group`() = runTest {
        projectDao.companyNames = mapOf("co1" to "Acme")
        projectDao.seed(
            ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD", status = ProjectStatus.ACTIVE),
            ProjectEntity(id = "p2", companyId = "co1", name = "Stocktake", startDate = jan1, currency = "USD", status = ProjectStatus.ACTIVE),
            ProjectEntity(id = "p3", companyId = "co1", name = "Audit", startDate = jan1, currency = "EUR", status = ProjectStatus.ACTIVE),
        )
        clerkDao.seed(
            ClerkEntity(id = "c1", name = "Ali"),
            ClerkEntity(id = "c2", name = "Sam"),
            ClerkEntity(id = "c3", name = "Rana"),
        )
        attendanceEntryDao.seed(
            AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 3000), // USD c1 owed 3000
            AttendanceEntryEntity("a2", "p2", "c2", jan1, present = true, rateSnapshot = 1000), // USD c2 owed 1000
            AttendanceEntryEntity("a3", "p3", "c3", jan1, present = true, rateSnapshot = 5000), // EUR c3 owed 5000
        )

        val vm = buildViewModel()
        var latest = HomeUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        // Outstanding: USD 4000 (3000+1000), EUR 5000 -> EUR group first (5000 > 4000), even
        // though USD has more owed CLERKS — group order follows the currency TOTAL, not count.
        assertEquals(2, latest.owedClerkGroups.size)

        val eurGroup = latest.owedClerkGroups[0]
        assertEquals("EUR", eurGroup.currency)
        assertEquals(1, eurGroup.clerks.size)
        assertEquals(5000L, eurGroup.clerks[0].owed)

        val usdGroup = latest.owedClerkGroups[1]
        assertEquals("USD", usdGroup.currency)
        assertEquals(2, usdGroup.clerks.size)
        // Sorted descending WITHIN the USD group, never compared against the EUR figures.
        assertEquals("c1", usdGroup.clerks[0].clerkId)
        assertEquals(3000L, usdGroup.clerks[0].owed)
        assertEquals("c2", usdGroup.clerks[1].clerkId)
        assertEquals(1000L, usdGroup.clerks[1].owed)

        job.cancel()
    }

    @Test
    fun `by-clerk view folds one clerk's projects into a single total per currency with a breakdown`() = runTest {
        projectDao.companyNames = mapOf("co1" to "Acme")
        projectDao.seed(
            ProjectEntity(id = "p1", companyId = "co1", name = "Warehouse", startDate = jan1, currency = "USD", status = ProjectStatus.ACTIVE),
            ProjectEntity(id = "p2", companyId = "co1", name = "Stocktake", startDate = jan1, currency = "USD", status = ProjectStatus.ACTIVE),
            ProjectEntity(id = "p3", companyId = "co1", name = "Audit", startDate = jan1, currency = "EUR", status = ProjectStatus.ACTIVE),
        )
        clerkDao.seed(ClerkEntity(id = "c1", name = "Ali"), ClerkEntity(id = "c2", name = "Sam"))
        attendanceEntryDao.seed(
            AttendanceEntryEntity("a1", "p1", "c1", jan1, present = true, rateSnapshot = 1000), // USD c1@p1 1000
            AttendanceEntryEntity("a2", "p2", "c1", jan1, present = true, rateSnapshot = 3000), // USD c1@p2 3000
            AttendanceEntryEntity("a3", "p2", "c2", jan1, present = true, rateSnapshot = 3500), // USD c2@p2 3500
            AttendanceEntryEntity("a4", "p3", "c1", jan1, present = true, rateSnapshot = 2000), // EUR c1@p3 2000
        )

        val vm = buildViewModel()
        var latest = HomeUiState()
        val job = launch(Dispatchers.Unconfined) { vm.uiState.collect { latest = it } }

        // USD 7500 before EUR 2000, mirroring the outstanding totals order.
        assertEquals(listOf("USD", "EUR"), latest.owedClerkTotalGroups.map { it.currency })

        val usd = latest.owedClerkTotalGroups[0].clerks
        assertEquals(listOf("c1", "c2"), usd.map { it.clerkId }) // 4000 > 3500
        assertEquals(4000L, usd[0].total)
        assertEquals(listOf("p2", "p1"), usd[0].projects.map { it.projectId }) // breakdown by amount
        assertEquals(3500L, usd[1].total)

        // Ali's EUR money stays in its own group — never summed with his USD total.
        val eur = latest.owedClerkTotalGroups[1].clerks
        assertEquals(1, eur.size)
        assertEquals(2000L, eur[0].total)

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
        assertTrue(latest.owedClerkGroups.isEmpty())
        assertTrue(latest.owedClerkTotalGroups.isEmpty())

        job.cancel()
    }
}

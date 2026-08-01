package com.vague.crewtally.ui.clerk

import com.vague.crewtally.data.local.AttendanceEntryEntity
import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.data.local.PaymentEntity
import com.vague.crewtally.data.local.RosterEntryEntity
import com.vague.crewtally.testutil.FakeAttendanceEntryDao
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.FakePaymentDao
import com.vague.crewtally.testutil.FakeRosterEntryDao
import com.vague.crewtally.testutil.MainDispatcherRule
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClerkFormViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun buildViewModel(
        clerkDao: FakeClerkDao = FakeClerkDao(),
        rosterDao: FakeRosterEntryDao = FakeRosterEntryDao(),
        attendanceDao: FakeAttendanceEntryDao = FakeAttendanceEntryDao(),
        paymentDao: FakePaymentDao = FakePaymentDao(),
        clerkId: String? = null,
    ) = ClerkFormViewModel(clerkDao, rosterDao, attendanceDao, paymentDao, clerkId)

    @Test
    fun `new clerk form starts blank and cannot save`() = runTest {
        // Arrange & Act
        val viewModel = buildViewModel()

        // Assert
        val state = viewModel.uiState.value
        assertEquals("", state.name)
        assertFalse(state.isEditing)
        assertFalse(state.canSave)
    }

    @Test
    fun `save is blocked while name is blank`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao()
        val viewModel = buildViewModel(clerkDao = clerkDao)

        // Act
        viewModel.onEvent(ClerkFormEvent.PhoneChanged("70111111"))
        viewModel.onEvent(ClerkFormEvent.Save)

        // Assert
        assertFalse(viewModel.uiState.value.isDone)
        assertTrue(clerkDao.all().isEmpty())
    }

    @Test
    fun `save creates a new clerk once name is filled in`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao()
        val viewModel = buildViewModel(clerkDao = clerkDao)

        // Act
        viewModel.onEvent(ClerkFormEvent.NameChanged("Jad Awad"))
        viewModel.onEvent(ClerkFormEvent.PhoneChanged("70111111"))
        viewModel.onEvent(ClerkFormEvent.Save)

        // Assert
        assertTrue(viewModel.uiState.value.isDone)
        val saved = clerkDao.all().single()
        assertEquals("Jad Awad", saved.name)
        assertEquals("70111111", saved.phone)
        assertTrue(saved.active)
    }

    @Test
    fun `editing an existing clerk loads its fields`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply {
            seed(ClerkEntity(id = "1", name = "Sara Khoury", phone = "70222222", notes = "Reliable", active = true))
        }

        // Act
        val viewModel = buildViewModel(clerkDao = clerkDao, clerkId = "1")

        // Assert
        val state = viewModel.uiState.value
        assertEquals("Sara Khoury", state.name)
        assertEquals("70222222", state.phone)
        assertEquals("Reliable", state.notes)
        assertFalse(state.isLoading)
        assertTrue(state.isEditing)
    }

    @Test
    fun `archive marks the clerk inactive without deleting it`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply {
            seed(ClerkEntity(id = "1", name = "Sara Khoury", active = true))
        }
        val viewModel = buildViewModel(clerkDao = clerkDao, clerkId = "1")

        // Act
        viewModel.onEvent(ClerkFormEvent.Archive)

        // Assert
        assertTrue(viewModel.uiState.value.isDone)
        assertTrue(viewModel.uiState.value.isArchived)
        assertEquals(false, clerkDao.getById("1")?.active)
    }

    @Test
    fun `unarchive marks the clerk active again`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply {
            seed(ClerkEntity(id = "1", name = "Sara Khoury", active = false))
        }
        val viewModel = buildViewModel(clerkDao = clerkDao, clerkId = "1")

        // Act
        viewModel.onEvent(ClerkFormEvent.Unarchive)

        // Assert
        assertFalse(viewModel.uiState.value.isArchived)
        assertEquals(true, clerkDao.getById("1")?.active)
    }

    @Test
    fun `delete is ineligible when the clerk has roster history`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "1", name = "Sara Khoury")) }
        val rosterDao = FakeRosterEntryDao().apply {
            seed(RosterEntryEntity(id = "r1", projectId = "p1", clerkId = "1", dailyRate = 5000))
        }

        // Act
        val viewModel = buildViewModel(clerkDao = clerkDao, rosterDao = rosterDao, clerkId = "1")

        // Assert
        assertFalse(viewModel.uiState.value.canDelete)
    }

    @Test
    fun `delete is ineligible when the clerk has attendance history`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "1", name = "Sara Khoury")) }
        val attendanceDao = FakeAttendanceEntryDao().apply {
            seed(
                AttendanceEntryEntity(
                    id = "a1",
                    projectId = "p1",
                    clerkId = "1",
                    date = LocalDate.of(2026, 8, 1),
                    present = true,
                    rateSnapshot = 5000,
                ),
            )
        }

        // Act
        val viewModel = buildViewModel(clerkDao = clerkDao, attendanceDao = attendanceDao, clerkId = "1")

        // Assert
        assertFalse(viewModel.uiState.value.canDelete)
    }

    @Test
    fun `delete is ineligible when the clerk has payment history`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "1", name = "Sara Khoury")) }
        val paymentDao = FakePaymentDao().apply {
            seed(PaymentEntity(id = "pay1", projectId = "p1", clerkId = "1", date = LocalDate.of(2026, 8, 1), amount = 1000))
        }

        // Act
        val viewModel = buildViewModel(clerkDao = clerkDao, paymentDao = paymentDao, clerkId = "1")

        // Assert
        assertFalse(viewModel.uiState.value.canDelete)
    }

    @Test
    fun `delete is eligible and removes the clerk when there is zero history`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "1", name = "Typo Clerk")) }
        val viewModel = buildViewModel(clerkDao = clerkDao, clerkId = "1")

        // Act
        assertTrue(viewModel.uiState.value.canDelete)
        viewModel.onEvent(ClerkFormEvent.Delete)

        // Assert
        assertTrue(viewModel.uiState.value.isDone)
        assertNull(clerkDao.getById("1"))
    }

    @Test
    fun `delete rechecks history and aborts if roster history appeared after load`() = runTest {
        // Arrange
        val clerkDao = FakeClerkDao().apply { seed(ClerkEntity(id = "1", name = "Typo Clerk")) }
        val rosterDao = FakeRosterEntryDao()
        val viewModel = buildViewModel(clerkDao = clerkDao, rosterDao = rosterDao, clerkId = "1")
        assertTrue(viewModel.uiState.value.canDelete)

        // Act: history appears (e.g. rostered from another screen) after the form loaded
        // but before Delete is tapped.
        rosterDao.seed(RosterEntryEntity(id = "r1", projectId = "p1", clerkId = "1", dailyRate = 5000))
        viewModel.onEvent(ClerkFormEvent.Delete)

        // Assert
        assertFalse(viewModel.uiState.value.canDelete)
        assertFalse(viewModel.uiState.value.isDone)
        assertEquals("Typo Clerk", clerkDao.getById("1")?.name)
    }
}

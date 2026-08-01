package com.vague.crewtally.ui.clerk

import com.vague.crewtally.data.local.ClerkEntity
import com.vague.crewtally.testutil.FakeClerkDao
import com.vague.crewtally.testutil.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ClerkListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val jad = ClerkEntity(id = "1", name = "Jad Awad", phone = "70111111", active = true)
    private val sara = ClerkEntity(id = "2", name = "Sara Khoury", phone = "70222222", active = true)
    private val retired = ClerkEntity(id = "3", name = "Amin Nasser", phone = "70333333", active = false)

    private fun buildViewModel(dao: FakeClerkDao) = ClerkListViewModel(dao)

    @Test
    fun `active clerks are listed and archived clerks are hidden by default`() = runTest {
        // Arrange
        val dao = FakeClerkDao().apply { seed(jad, sara, retired) }
        val viewModel = buildViewModel(dao)
        // uiState is a stateIn(WhileSubscribed) flow — it only starts combining once
        // something collects it, so the test needs its own collector, pumped to
        // completion, before reading .value.
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Act
        val state = viewModel.uiState.value

        // Assert
        assertEquals(setOf("Jad Awad", "Sara Khoury"), state.activeClerks.map { it.name }.toSet())
        assertTrue(state.archivedClerks.isEmpty())
        assertFalse(state.showArchived)
        job.cancel()
    }

    @Test
    fun `search query filters active clerks by name or phone`() = runTest {
        // Arrange
        val dao = FakeClerkDao().apply { seed(jad, sara) }
        val viewModel = buildViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ClerkListEvent.SearchQueryChanged("70222"))
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertEquals(listOf("Sara Khoury"), state.activeClerks.map { it.name })
        job.cancel()
    }

    @Test
    fun `show archived toggle reveals the archived section`() = runTest {
        // Arrange
        val dao = FakeClerkDao().apply { seed(jad, retired) }
        val viewModel = buildViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Act
        viewModel.onEvent(ClerkListEvent.ShowArchivedChanged(true))
        advanceUntilIdle()

        // Assert
        val state = viewModel.uiState.value
        assertTrue(state.showArchived)
        assertEquals(listOf("Amin Nasser"), state.archivedClerks.map { it.name })
        job.cancel()
    }

    @Test
    fun `hasAnyClerks is false only when the store is genuinely empty`() = runTest {
        // Arrange
        val dao = FakeClerkDao()
        val viewModel = buildViewModel(dao)
        val job = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // Act
        val emptyState = viewModel.uiState.value
        dao.seed(retired) // only an archived clerk exists — still "has any clerks"
        advanceUntilIdle()
        val nonEmptyState = viewModel.uiState.value

        // Assert
        assertFalse(emptyState.hasAnyClerks)
        assertTrue(nonEmptyState.hasAnyClerks)
        job.cancel()
    }
}

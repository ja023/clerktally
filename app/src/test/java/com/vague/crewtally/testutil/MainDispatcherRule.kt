package com.vague.crewtally.testutil

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher around every test — required because
 * every ViewModel here uses `viewModelScope` (= `Dispatchers.Main.immediate`), which
 * doesn't exist on the plain JVM. [UnconfinedTestDispatcher] is the default because it
 * runs launched coroutines eagerly, so a ViewModel's `stateIn(...)` reflects a fake DAO's
 * current value as soon as something collects it — no manual `advanceUntilIdle()`
 * choreography needed in most tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

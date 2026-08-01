package com.vague.crewtally.ui.viewmodel

/**
 * Shared [kotlinx.coroutines.flow.SharingStarted.WhileSubscribed] grace period for every
 * `stateIn(...)` in the app: long enough to survive a configuration change without
 * re-querying Room, short enough that a screen the user actually left stops collecting.
 */
const val STOP_TIMEOUT_MILLIS = 5_000L

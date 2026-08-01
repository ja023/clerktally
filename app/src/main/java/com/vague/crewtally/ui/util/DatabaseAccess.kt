package com.vague.crewtally.ui.util

import android.content.Context
import com.vague.crewtally.CrewTallyApplication
import com.vague.crewtally.data.local.CrewTallyDatabase

/**
 * Reaches the process-wide Room database from any Compose call site. This is the whole
 * "repository" seam Phase 0 set up (no DI framework yet) — screens build their ViewModel's
 * factory by pulling the DAOs they need straight off this database.
 */
fun Context.crewTallyDatabase(): CrewTallyDatabase =
    (applicationContext as CrewTallyApplication).database

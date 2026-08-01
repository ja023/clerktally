package com.vague.crewtally

import android.app.Application
import com.vague.crewtally.data.local.CrewTallyDatabase

/**
 * Application entry point. Owns the single Room database instance for the process, which
 * later phases read through (repositories, then ViewModels). Kept deliberately minimal for
 * Phase 0 — no DI framework yet; the database singleton is the only shared dependency so far.
 */
class CrewTallyApplication : Application() {

    /** The process-wide database. Built lazily on first access, after the app Context exists. */
    val database: CrewTallyDatabase by lazy { CrewTallyDatabase.getInstance(this) }
}

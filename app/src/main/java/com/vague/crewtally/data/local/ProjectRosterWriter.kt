package com.vague.crewtally.data.local

import androidx.room.withTransaction
import java.util.UUID

/**
 * Creates a project and its initial roster together. A project should never exist for a
 * moment with zero of its intended roster rows (or vice versa) if the app is killed
 * mid-save, so the write is one atomic unit rather than two separate DAO calls.
 *
 * Every screen's ViewModel factory pulls [CrewTallyApplication.projectRosterWriter] (the ONE
 * app-wide instance) rather than constructing its own `RoomProjectRosterWriter(database)`, for
 * the same sharing reason [PaymentWriter] and [AttendanceWriter] do.
 *
 * An interface (not a concrete class) so ViewModel tests can substitute an in-memory fake
 * instead of exercising a real Room transaction.
 */
interface ProjectRosterWriter {
    suspend fun createProjectWithRoster(project: ProjectEntity, rosterEntries: List<RosterEntryEntity>)

    /**
     * Atomically adds (or re-adds) one clerk to a project's roster: looks up the existing
     * (non-removed or removed) row for this (projectId, clerkId) pair and reuses its id, or
     * mints a new one if the pair has never been rostered. The lookup and the write MUST run
     * inside a single transaction — split into two DAO calls (as the ViewModel used to do
     * directly), a second write that starts after the lookup but before the first write
     * finishes would also see "no existing row" and mint its own fresh UUID. [RosterEntryDao.upsert]
     * inserts that fresh id; since the (projectId, clerkId) unique index already has a row
     * for the pair, the insert fails and Room's upsert falls back to an UPDATE by primary
     * key — which matches zero rows for a fresh UUID, so the write is silently dropped
     * instead of erroring. See [RosterEntryDao.upsert]'s KDoc for the invariant this method
     * exists to protect.
     */
    suspend fun upsertRosterEntryForPair(projectId: String, clerkId: String, dailyRate: Long)
}

/** Production [ProjectRosterWriter] backed by a real [CrewTallyDatabase] transaction. */
class RoomProjectRosterWriter(private val database: CrewTallyDatabase) : ProjectRosterWriter {

    override suspend fun createProjectWithRoster(
        project: ProjectEntity,
        rosterEntries: List<RosterEntryEntity>,
    ) {
        database.withTransaction {
            database.projectDao().upsert(project)
            rosterEntries.forEach { database.rosterEntryDao().upsert(it) }
        }
    }

    override suspend fun upsertRosterEntryForPair(projectId: String, clerkId: String, dailyRate: Long) {
        database.withTransaction {
            val dao = database.rosterEntryDao()
            val existing = dao.getForPair(projectId, clerkId)
            val entry = (
                existing ?: RosterEntryEntity(
                    id = UUID.randomUUID().toString(),
                    projectId = projectId,
                    clerkId = clerkId,
                    dailyRate = dailyRate,
                )
                ).copy(dailyRate = dailyRate, removedAt = null)
            dao.upsert(entry)
        }
    }
}

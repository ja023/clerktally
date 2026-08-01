package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectRosterWriter
import com.vague.crewtally.data.local.RosterEntryEntity
import java.util.UUID

/**
 * Records the last [createProjectWithRoster] call and forwards it into the fake DAOs, so
 * tests can assert both "what was saved" and "did it land in the data layer" without a real
 * Room transaction.
 */
class FakeProjectRosterWriter(
    private val projectDao: FakeProjectDao,
    private val rosterEntryDao: FakeRosterEntryDao,
) : ProjectRosterWriter {

    var callCount: Int = 0
        private set
    var lastProject: ProjectEntity? = null
        private set
    var lastRosterEntries: List<RosterEntryEntity> = emptyList()
        private set

    var upsertRosterEntryForPairCallCount: Int = 0
        private set

    override suspend fun createProjectWithRoster(project: ProjectEntity, rosterEntries: List<RosterEntryEntity>) {
        callCount += 1
        lastProject = project
        lastRosterEntries = rosterEntries
        projectDao.upsert(project)
        rosterEntries.forEach { rosterEntryDao.upsert(it) }
    }

    /**
     * Mirrors [com.vague.crewtally.data.local.RoomProjectRosterWriter]'s real semantics: look
     * up the existing row for this (projectId, clerkId) pair and reuse its id (clearing
     * removedAt), or mint a new one — so tests exercising this fake catch the same "reuse the
     * existing id" bug a bare getForPair + upsert in the ViewModel used to risk.
     */
    override suspend fun upsertRosterEntryForPair(projectId: String, clerkId: String, dailyRate: Long) {
        upsertRosterEntryForPairCallCount += 1
        val existing = rosterEntryDao.getForPair(projectId, clerkId)
        val entry = (
            existing ?: RosterEntryEntity(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                clerkId = clerkId,
                dailyRate = dailyRate,
            )
            ).copy(dailyRate = dailyRate, removedAt = null)
        rosterEntryDao.upsert(entry)
    }
}

package com.vague.crewtally.testutil

import com.vague.crewtally.data.local.ProjectEntity
import com.vague.crewtally.data.local.ProjectRosterWriter
import com.vague.crewtally.data.local.RosterEntryEntity

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

    override suspend fun createProjectWithRoster(project: ProjectEntity, rosterEntries: List<RosterEntryEntity>) {
        callCount += 1
        lastProject = project
        lastRosterEntries = rosterEntries
        projectDao.upsert(project)
        rosterEntries.forEach { rosterEntryDao.upsert(it) }
    }
}

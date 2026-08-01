package com.vague.crewtally.data.local

import androidx.room.withTransaction

/**
 * Creates a project and its initial roster together. A project should never exist for a
 * moment with zero of its intended roster rows (or vice versa) if the app is killed
 * mid-save, so the write is one atomic unit rather than two separate DAO calls.
 *
 * An interface (not a concrete class) so ViewModel tests can substitute an in-memory fake
 * instead of exercising a real Room transaction.
 */
interface ProjectRosterWriter {
    suspend fun createProjectWithRoster(project: ProjectEntity, rosterEntries: List<RosterEntryEntity>)
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
}

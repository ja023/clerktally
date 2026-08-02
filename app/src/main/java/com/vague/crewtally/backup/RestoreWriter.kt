package com.vague.crewtally.backup

import androidx.room.withTransaction
import com.vague.crewtally.data.local.CrewTallyDatabase
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The atomic write seam for a full-database restore, mirroring [com.vague.crewtally.data.local.PaymentWriter]
 * and friends: one method, one transaction, one writer-scoped [Mutex] so a restore can never
 * interleave with itself, and an interface so ViewModel tests substitute an in-memory fake
 * instead of a real Room transaction (see `FakeRestoreWriter` / `RestoreWriterTest`'s atomicity
 * case, which mirrors `AttendanceWriterTest`'s `FaultyWalkInWriter` pattern).
 *
 * [restore] REPLACES every row in all 7 tables with [payload]'s rows — [BackupValidator] has
 * already proven the payload is internally consistent (schema version, required fields, FK
 * references) before this is ever called, so this layer's only remaining job is doing the
 * delete-then-insert atomically.
 */
interface RestoreWriter {
    suspend fun restore(payload: BackupPayload)
}

/** Production [RestoreWriter] backed by a real [CrewTallyDatabase] transaction. */
class RoomRestoreWriter(private val database: CrewTallyDatabase) : RestoreWriter {

    private val mutex = Mutex()

    override suspend fun restore(payload: BackupPayload) {
        mutex.withLock {
            database.withTransaction {
                // Delete leaves-first so every RESTRICT foreign key stays satisfied mid-wipe —
                // the schema has exactly one CASCADE (extra_pay_lines off attendance_entries),
                // everything else is RESTRICT, so a parent can never be deleted while a child
                // referencing it still exists.
                database.extraPayLineDao().deleteAll()
                database.paymentDao().deleteAll()
                database.attendanceEntryDao().deleteAll()
                database.rosterEntryDao().deleteAll()
                database.projectDao().deleteAll()
                database.clerkDao().deleteAll()
                database.companyDao().deleteAll()

                // Insert parents-first, the mirror order, so every foreign key the inserts
                // themselves establish already has its target row present.
                database.companyDao().upsertAll(payload.companies.map { it.toEntity() })
                database.clerkDao().upsertAll(payload.clerks.map { it.toEntity() })
                database.projectDao().upsertAll(payload.projects.map { it.toEntity() })
                database.rosterEntryDao().upsertAll(payload.rosterEntries.map { it.toEntity() })
                database.attendanceEntryDao().upsertAll(payload.attendanceEntries.map { it.toEntity() })
                database.extraPayLineDao().upsertAll(payload.extraPayLines.map { it.toEntity() })
                database.paymentDao().upsertAll(payload.payments.map { it.toEntity() })
            }
        }
    }
}

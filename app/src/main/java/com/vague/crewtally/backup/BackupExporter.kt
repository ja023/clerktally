package com.vague.crewtally.backup

import androidx.room.withTransaction
import com.vague.crewtally.data.local.CrewTallyDatabase

/**
 * The atomic read seam for a whole-database export, mirroring [RestoreWriter]/[RoomRestoreWriter]:
 * an interface so ViewModel tests substitute an in-memory fake instead of a real Room transaction,
 * with the production implementation reading through the same [CrewTallyDatabase] instance the
 * rest of the app writes through.
 */
interface BackupExporter {
    suspend fun export(appVersionName: String, exportedAtEpochMillis: Long): BackupPayload
}

/**
 * Production [BackupExporter]. All 7 `getAll()` reads run inside ONE [CrewTallyDatabase.withTransaction]
 * — without it, a write landing between two of the reads (e.g. a payment recorded while the
 * export is mid-flight) would export a payload where some tables reflect the moment before that
 * write and others reflect the moment after, an internally-inconsistent snapshot no single point
 * in time actually looked like. Wrapping the reads in a transaction (even though nothing here
 * writes) pins every table to the same instant, matching a backup's promise of being a snapshot.
 */
class RoomBackupExporter(private val database: CrewTallyDatabase) : BackupExporter {

    override suspend fun export(appVersionName: String, exportedAtEpochMillis: Long): BackupPayload =
        database.withTransaction {
            BackupPayload(
                schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
                exportedAt = exportedAtEpochMillis,
                appVersionName = appVersionName,
                companies = database.companyDao().getAll().map { it.toDto() },
                clerks = database.clerkDao().getAll().map { it.toDto() },
                projects = database.projectDao().getAll().map { it.toDto() },
                rosterEntries = database.rosterEntryDao().getAll().map { it.toDto() },
                attendanceEntries = database.attendanceEntryDao().getAll().map { it.toDto() },
                extraPayLines = database.extraPayLineDao().getAll().map { it.toDto() },
                payments = database.paymentDao().getAll().map { it.toDto() },
            )
        }
}

package com.vague.crewtally.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The app's Room database. Offline-first, single device, no sync (LOCKED decision #1).
 *
 * `exportSchema = true`: every build writes the schema JSON to `app/schemas/`, which is
 * committed to version control so future migrations can be diffed and reviewed. Version 1 is
 * the initial schema; migrations get added here as the schema evolves.
 *
 * Foreign-key constraints are enforced by Room automatically (it issues
 * `PRAGMA foreign_keys = ON`), so the RESTRICT/CASCADE rules on the entities are live at
 * runtime.
 */
@Database(
    entities = [
        CompanyEntity::class,
        ClerkEntity::class,
        ProjectEntity::class,
        RosterEntryEntity::class,
        AttendanceEntryEntity::class,
        ExtraPayLineEntity::class,
        PaymentEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class CrewTallyDatabase : RoomDatabase() {

    abstract fun companyDao(): CompanyDao

    abstract fun clerkDao(): ClerkDao

    abstract fun projectDao(): ProjectDao

    abstract fun rosterEntryDao(): RosterEntryDao

    abstract fun attendanceEntryDao(): AttendanceEntryDao

    abstract fun extraPayLineDao(): ExtraPayLineDao

    abstract fun paymentDao(): PaymentDao

    companion object {
        private const val DATABASE_NAME = "crewtally.db"

        @Volatile
        private var instance: CrewTallyDatabase? = null

        /** Process-wide singleton. Double-checked locking so one instance is ever built. */
        fun getInstance(context: Context): CrewTallyDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context).also { instance = it }
            }

        private fun build(context: Context): CrewTallyDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                CrewTallyDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}

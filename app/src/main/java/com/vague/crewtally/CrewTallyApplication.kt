package com.vague.crewtally

import android.app.Application
import com.vague.crewtally.data.local.AttendanceWriter
import com.vague.crewtally.data.local.CrewTallyDatabase
import com.vague.crewtally.data.local.PaymentWriter
import com.vague.crewtally.data.local.ProjectRosterWriter
import com.vague.crewtally.data.local.RoomAttendanceWriter
import com.vague.crewtally.data.local.RoomPaymentWriter
import com.vague.crewtally.data.local.RoomProjectRosterWriter

/**
 * Application entry point. Owns the single Room database instance for the process, which
 * later phases read through (repositories, then ViewModels). Kept deliberately minimal —
 * no DI framework; the database singleton and the three money/attendance writers below are
 * the only shared dependencies.
 */
class CrewTallyApplication : Application() {

    /** The process-wide database. Built lazily on first access, after the app Context exists. */
    val database: CrewTallyDatabase by lazy { CrewTallyDatabase.getInstance(this) }

    /**
     * The ONE app-wide [PaymentWriter], [AttendanceWriter], and [ProjectRosterWriter]
     * instances — every screen's ViewModel factory must obtain these rather than constructing
     * its own `Room*Writer(database)`. Each writer's KDoc promises its internal mutex
     * serializes writes fired from different screens; that guarantee only holds if every
     * screen shares the same instance (and therefore the same mutex). A screen that built its
     * own writer would get its own private mutex, silently defeating the guarantee while the
     * KDoc kept claiming it held. Built lazily alongside [database], after the app Context
     * exists.
     */
    val paymentWriter: PaymentWriter by lazy { RoomPaymentWriter(database) }
    val attendanceWriter: AttendanceWriter by lazy { RoomAttendanceWriter(database) }
    val projectRosterWriter: ProjectRosterWriter by lazy { RoomProjectRosterWriter(database) }
}

package com.vague.crewtally.ui.screen.attendance

import android.net.Uri
import java.time.LocalDate

/**
 * Route templates and type-safe builders for the Phase 3 attendance destinations. Dates travel
 * as an epoch-day [Long] (LocalDate.toEpochDay) — a stable calendar-day integer with no zone,
 * matching how [LocalDate] is stored. Free-text args (clerk name, currency) are URL-encoded.
 */
object AttendanceRoutes {
    const val PROJECT_ID = "projectId"
    const val EPOCH_DAY = "epochDay"
    const val CLERK_ID = "clerkId"
    const val CLERK_NAME = "clerkName"
    const val RATE = "rate"
    const val CURRENCY = "currency"

    const val DAY_TEMPLATE = "project/{$PROJECT_ID}/attendance/{$EPOCH_DAY}"
    const val EXTRAS_TEMPLATE =
        "project/{$PROJECT_ID}/attendance/{$EPOCH_DAY}/extras/{$CLERK_ID}/{$CLERK_NAME}/{$RATE}/{$CURRENCY}"
    const val WALK_IN_TEMPLATE =
        "project/{$PROJECT_ID}/attendance/{$EPOCH_DAY}/walkin/{$CURRENCY}"

    fun day(projectId: String, date: LocalDate): String =
        "project/$projectId/attendance/${date.toEpochDay()}"

    fun extras(
        projectId: String,
        date: LocalDate,
        clerkId: String,
        clerkName: String,
        rateMinorUnits: Long,
        currency: String,
    ): String = "project/$projectId/attendance/${date.toEpochDay()}/extras/$clerkId/" +
        "${Uri.encode(clerkName)}/$rateMinorUnits/${Uri.encode(currency)}"

    fun walkIn(projectId: String, date: LocalDate, currency: String): String =
        "project/$projectId/attendance/${date.toEpochDay()}/walkin/${Uri.encode(currency)}"
}

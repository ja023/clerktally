package com.vague.crewtally.data.contacts

import android.content.Context

/**
 * Remembers, across app launches, whether CrewTally has already asked once for
 * `READ_CONTACTS`. Backed by `SharedPreferences` (not just in-memory Compose state)
 * because the "ask once, never nag" rule (Phase 1 LOCKED decisions) must survive the
 * form being left and reopened, not just one composition.
 *
 * The system permission dialog itself only reappears while the user hasn't ticked
 * "don't ask again" — re-requesting after a plain denial would show it again and read
 * as nagging. Gating the request on this flag, rather than only on the live grant
 * state, is what keeps the ask to exactly once for the lifetime of the install.
 */
class ContactsPermissionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var hasRequestedOnce: Boolean
        get() = prefs.getBoolean(KEY_HAS_REQUESTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HAS_REQUESTED, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "crewtally_contacts_prefs"
        const val KEY_HAS_REQUESTED = "has_requested_read_contacts"
    }
}

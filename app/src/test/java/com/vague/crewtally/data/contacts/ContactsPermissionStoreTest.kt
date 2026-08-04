package com.vague.crewtally.data.contacts

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the "ask once, never nag" contract from [ContactsPermissionStore]'s KDoc: the flag
 * starts false, flips true once set, and — because it's `SharedPreferences`-backed rather than
 * in-memory — survives a fresh instance built from the same [android.content.Context], the way
 * it must survive the form being left and reopened.
 */
@RunWith(RobolectricTestRunner::class)
class ContactsPermissionStoreTest {

    private val context: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `hasRequestedOnce is false before any request is made`() {
        val store = ContactsPermissionStore(context)

        assertFalse(store.hasRequestedOnce)
    }

    @Test
    fun `hasRequestedOnce is true immediately after being set`() {
        val store = ContactsPermissionStore(context)

        store.hasRequestedOnce = true

        assertTrue(store.hasRequestedOnce)
    }

    @Test
    fun `hasRequestedOnce persists across store instances`() {
        val first = ContactsPermissionStore(context)
        first.hasRequestedOnce = true

        val second = ContactsPermissionStore(context)

        assertTrue(second.hasRequestedOnce)
    }
}

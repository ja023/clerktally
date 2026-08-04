package com.vague.crewtally.data.contacts

import android.Manifest
import android.app.Application
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import com.vague.crewtally.testutil.FakeContact
import com.vague.crewtally.testutil.FakeContactsProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * Exercises [ContactsSearcher] against [FakeContactsProvider], a Robolectric-registered stand-in
 * for the real `ContactsContract` provider. Covers the review-debt items from CLAUDE.md: `LIKE`
 * escaping, name+phone union with dedupe, and the suggestion cap.
 */
@RunWith(RobolectricTestRunner::class)
class ContactsSearcherTest {

    private lateinit var context: Application
    private lateinit var provider: FakeContactsProvider
    private lateinit var searcher: ContactsSearcher

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        provider = Robolectric.buildContentProvider(FakeContactsProvider::class.java)
            .create(ContactsContract.AUTHORITY)
            .get()
        searcher = ContactsSearcher(context)
    }

    private fun grantContactsPermission() {
        shadowOf(context).grantPermissions(Manifest.permission.READ_CONTACTS)
    }

    @Test
    fun `search returns empty list for a blank query`() = runTest {
        grantContactsPermission()
        provider.contacts = listOf(FakeContact(id = "1", name = "Ali", phone = "70111111"))

        val result = searcher.search("   ")

        assertEquals(emptyList<ContactSuggestion>(), result)
    }

    @Test
    fun `search returns empty list when contacts permission has not been granted`() = runTest {
        provider.contacts = listOf(FakeContact(id = "1", name = "Ali", phone = "70111111"))

        val result = searcher.search("Ali")

        assertEquals(emptyList<ContactSuggestion>(), result)
    }

    @Test
    fun `search escapes a literal percent so it is not treated as a wildcard`() = runTest {
        grantContactsPermission()
        provider.contacts = listOf(
            FakeContact(id = "1", name = "50% Off Sale", phone = "70111111"),
            FakeContact(id = "2", name = "5000 Items Co", phone = "70222222"),
        )

        val result = searcher.search("50%")

        assertEquals(listOf("50% Off Sale"), result.map { it.name })
    }

    @Test
    fun `search escapes a literal underscore so it is not treated as a single-char wildcard`() = runTest {
        grantContactsPermission()
        provider.contacts = listOf(
            FakeContact(id = "1", name = "abc_def", phone = "70111111"),
            FakeContact(id = "2", name = "abcXdef", phone = "70222222"),
        )

        val result = searcher.search("c_d")

        assertEquals(listOf("abc_def"), result.map { it.name })
    }

    @Test
    fun `search escapes a literal backslash in the query so it does not alter the escape sequence`() = runTest {
        grantContactsPermission()
        provider.contacts = listOf(
            FakeContact(id = "1", name = "50\\_x Corp", phone = "70111111"),
            FakeContact(id = "2", name = "50_x Corp", phone = "70222222"),
        )

        val result = searcher.search("50\\_x")

        assertEquals(listOf("50\\_x Corp"), result.map { it.name })
    }

    @Test
    fun `search unions name and phone matches without duplicating a contact found by both`() = runTest {
        grantContactsPermission()
        // "222" appears in some names and some phone numbers; contact 3 matches both ways and
        // must surface exactly once.
        provider.contacts = listOf(
            FakeContact(id = "1", name = "Nadia 222", phone = "70111111"), // name match only
            FakeContact(id = "2", name = "Omar Khalil", phone = "70222222"), // phone match only
            FakeContact(id = "3", name = "Contact 222 Both", phone = "71222000"), // matches both
        )

        val result = searcher.search("222")

        assertEquals(3, result.map { it.id }.toSet().size)
        assertTrue(result.count { it.id == "3" } == 1)
    }

    @Test
    fun `search caps combined suggestions at the limit even when more contacts match`() = runTest {
        grantContactsPermission()
        // 4 name matches + 4 phone matches, one overlapping (id 3) => 7 distinct matches, more
        // than MAX_CONTACT_SUGGESTIONS.
        provider.contacts = listOf(
            FakeContact(id = "1", name = "Nadia 222", phone = "70111111"), // name only
            FakeContact(id = "2", name = "Omar Khalil", phone = "70222222"), // phone only
            FakeContact(id = "3", name = "Contact 222 Both", phone = "71222000"), // both
            FakeContact(id = "4", name = "Rami", phone = "70333222"), // phone only
            FakeContact(id = "5", name = "Sami 222x", phone = "70444444"), // name only
            FakeContact(id = "6", name = "Tarek", phone = "70555222"), // phone only
            FakeContact(id = "7", name = "Extra 222", phone = "70666666"), // name only
        )

        val result = searcher.search("222")

        assertEquals(MAX_CONTACT_SUGGESTIONS, result.size)
        assertEquals(result.size, result.map { it.id }.toSet().size)
    }
}

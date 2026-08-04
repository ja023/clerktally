package com.vague.crewtally.testutil

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data

/** One device address-book row, as [FakeContactsProvider] serves it. */
data class FakeContact(val id: String, val name: String, val phone: String)

/**
 * In-JVM stand-in for the real `ContactsContract` provider, registered with Robolectric's
 * `ShadowContentResolver` under [android.provider.ContactsContract.AUTHORITY] so
 * [com.vague.crewtally.data.contacts.ContactsSearcher] can run its real
 * `context.contentResolver.query(...)` calls against it unmodified.
 *
 * Serves the same three URIs the searcher queries:
 * - [Contacts.CONTENT_URI]: name search, matched with a real SQL `LIKE ... ESCAPE '\'`
 *   interpreter ([likeToRegex]) so a test can prove the searcher's escaping actually changes
 *   matching behavior, not just that it builds a particular string.
 * - [Phone.CONTENT_FILTER_URI]: phone search, matched by digit-normalised substring — the
 *   query term arrives as the URI's last path segment, exactly as the searcher builds it.
 * - [Phone.CONTENT_URI] (via [Data.CONTENT_URI]'s `data/phones` path): batched phone lookup
 *   by contact id, for the searcher's second pass that fills in each suggestion's number.
 */
class FakeContactsProvider : ContentProvider() {

    var contacts: List<FakeContact> = emptyList()

    private val matcher = UriMatcher(UriMatcher.NO_MATCH).apply {
        addURI(AUTHORITY, "contacts", MATCH_CONTACTS)
        addURI(AUTHORITY, "data/phones", MATCH_PHONES)
        addURI(AUTHORITY, "data/phones/filter/*", MATCH_PHONES_FILTER)
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor = when (matcher.match(uri)) {
        MATCH_CONTACTS -> queryByName(selectionArgs)
        MATCH_PHONES_FILTER -> queryByPhoneFilter(uri)
        MATCH_PHONES -> queryPhonesByContactIds(selectionArgs)
        else -> error("FakeContactsProvider: unmatched uri $uri")
    }

    private fun queryByName(selectionArgs: Array<out String>?): Cursor {
        val pattern = selectionArgs?.getOrNull(0)
        val regex = pattern?.let { likeToRegex(it) }
        val cursor = MatrixCursor(arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY))
        contacts
            .filter { regex == null || regex.matches(it.name) }
            .sortedBy { it.name.lowercase() }
            .forEach { cursor.addRow(arrayOf(it.id, it.name)) }
        return cursor
    }

    private fun queryByPhoneFilter(uri: Uri): Cursor {
        val term = uri.lastPathSegment.orEmpty()
        val normalizedTerm = term.filter { it.isDigit() }
        val cursor = MatrixCursor(arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY))
        contacts
            .filter { normalizedTerm.isNotEmpty() && it.phone.filter { ch -> ch.isDigit() }.contains(normalizedTerm) }
            .sortedBy { it.name.lowercase() }
            .forEach { cursor.addRow(arrayOf(it.id, it.name)) }
        return cursor
    }

    private fun queryPhonesByContactIds(selectionArgs: Array<out String>?): Cursor {
        val ids = selectionArgs.orEmpty().toSet()
        val cursor = MatrixCursor(arrayOf(Data.CONTACT_ID, Phone.NUMBER))
        contacts
            .filter { it.id in ids }
            .forEach { cursor.addRow(arrayOf(it.id, it.phone)) }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    private companion object {
        const val AUTHORITY = "com.android.contacts"
        const val MATCH_CONTACTS = 1
        const val MATCH_PHONES = 2
        const val MATCH_PHONES_FILTER = 3
    }
}

/**
 * Translates a SQL `LIKE` [pattern] (with `\` as the `ESCAPE` character, matching
 * [com.vague.crewtally.data.contacts.ContactsSearcher]'s query building) into a case-insensitive
 * [Regex]: `%` becomes `.*`, `_` becomes any single character, and an escape char followed by
 * another character is taken literally — the same semantics SQLite applies.
 */
internal fun likeToRegex(pattern: String): Regex {
    val builder = StringBuilder()
    var i = 0
    while (i < pattern.length) {
        val char = pattern[i]
        if (char == '\\' && i + 1 < pattern.length) {
            builder.append(Regex.escape(pattern[i + 1].toString()))
            i += 2
            continue
        }
        when (char) {
            '%' -> builder.append(".*")
            '_' -> builder.append(".")
            else -> builder.append(Regex.escape(char.toString()))
        }
        i++
    }
    return Regex("^$builder$", RegexOption.IGNORE_CASE)
}

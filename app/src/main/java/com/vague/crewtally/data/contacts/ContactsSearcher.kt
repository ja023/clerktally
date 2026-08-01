package com.vague.crewtally.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import androidx.core.content.ContextCompat
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Suggestion list is capped so the field reads as hints, not a contact browser. */
const val MAX_CONTACT_SUGGESTIONS = 5

/** Single backslash — the `ESCAPE` character used to neutralise `%`/`_` in `LIKE` queries. */
private const val LIKE_ESCAPE_CHAR = "\\"

/**
 * `ContactsContract`-backed contact search for the shared [com.vague.crewtally.ui.components.ContactSearchField].
 * Reads the device address book live; writes nothing, caches nothing between calls.
 *
 * Matches [query] against **display name and phone number** in two provider passes unioned
 * on contact id (name matches first, since a typed name is the common case here — unlike
 * JAPPED's contact sheet, CrewTally doesn't search email, so this stays a two-pass search):
 *
 * - Name: `Contacts.CONTENT_URI` with a `LIKE` on the display name, so contacts with no
 *   number at all are still findable while the person is typed.
 * - Phone: `Phone.CONTENT_FILTER_URI`, letting the platform's normalised/minimal-match
 *   indexing do the work instead of hand-stripping punctuation. Only run when [query]
 *   contains a digit, so a purely alphabetic query doesn't repeat the name pass for free.
 *
 * Never throws for permissions: a missing (or mid-use revoked) `READ_CONTACTS` grant
 * yields an empty list rather than propagating a `SecurityException`.
 */
class ContactsSearcher(private val context: Context) {

    suspend fun search(query: String, limit: Int = MAX_CONTACT_SUGGESTIONS): List<ContactSuggestion> {
        val term = query.trim()
        if (term.isEmpty() || limit <= 0) return emptyList()
        if (!hasContactsPermission()) return emptyList()

        return withContext(Dispatchers.IO) {
            val byName = queryNames(term, limit)
            val byPhone: Map<String, String> =
                if (term.any { it.isDigit() }) queryPhoneMatches(term, limit) else emptyMap()

            // The union IS the dedupe: keyed on the platform contact id, first-seen wins,
            // so a contact matching on both name and number is never listed twice.
            val orderedNames = LinkedHashMap<String, String>()
            for ((id, name) in byName) {
                if (orderedNames.size >= limit) break
                orderedNames.putIfAbsent(id, name)
            }
            for ((id, name) in byPhone) {
                if (orderedNames.size >= limit) break
                orderedNames.putIfAbsent(id, name)
            }
            if (orderedNames.isEmpty()) return@withContext emptyList()

            val ids = orderedNames.keys.toList()
            val phonesById = queryFirstPhonePerContact(ids)

            orderedNames.map { (id, name) ->
                ContactSuggestion(id = id, name = name, phone = phonesById[id].orEmpty())
            }
        }
    }

    private fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Contact id -> display name for contacts whose name matches [term], name-ordered. */
    private suspend fun queryNames(term: String, limit: Int): Map<String, String> {
        val selection = "${Contacts.DISPLAY_NAME_PRIMARY} LIKE ? ESCAPE '$LIKE_ESCAPE_CHAR'"
        val args = arrayOf("%${escapeForLike(term)}%")

        return guarded(emptyMap()) {
            context.contentResolver.query(
                Contacts.CONTENT_URI,
                arrayOf(Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY),
                selection,
                args,
                "${Contacts.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { cursor -> readIdToName(cursor, Contacts._ID, Contacts.DISPLAY_NAME_PRIMARY, limit) }
                .orEmpty()
        }
    }

    /**
     * Contact id -> display name for contacts whose number matches [term]. [term] is a URI
     * path segment here, not a selection argument, so it must be encoded: an unencoded `+`
     * country prefix would otherwise be read as an encoded space.
     */
    private suspend fun queryPhoneMatches(term: String, limit: Int): Map<String, String> =
        guarded(emptyMap()) {
            val uri = Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, Uri.encode(term))
            context.contentResolver.query(
                uri,
                arrayOf(Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY),
                null,
                null,
                "${Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { cursor -> readIdToName(cursor, Phone.CONTACT_ID, Phone.DISPLAY_NAME_PRIMARY, limit) }
                .orEmpty()
        }

    /** First non-blank phone number for each of [ids], batched into one query. */
    private suspend fun queryFirstPhonePerContact(ids: List<String>): Map<String, String> {
        if (ids.isEmpty()) return emptyMap()
        val placeholders = ids.joinToString(separator = ",") { "?" }
        val selection = "${Data.CONTACT_ID} IN ($placeholders)"

        return guarded(emptyMap()) {
            context.contentResolver.query(
                Phone.CONTENT_URI,
                arrayOf(Data.CONTACT_ID, Phone.NUMBER),
                selection,
                ids.toTypedArray(),
                null,
            )?.use { cursor -> readFirstValuePerId(cursor, Data.CONTACT_ID, Phone.NUMBER) }.orEmpty()
        }
    }

    private fun readIdToName(cursor: Cursor, idColumn: String, nameColumn: String, limit: Int): Map<String, String> {
        val idIndex = cursor.getColumnIndex(idColumn)
        val nameIndex = cursor.getColumnIndex(nameColumn)
        if (idIndex < 0 || nameIndex < 0) return emptyMap()

        val result = LinkedHashMap<String, String>()
        while (result.size < limit && cursor.moveToNext()) {
            val id = cursor.getString(idIndex)?.takeIf { it.isNotBlank() } ?: continue
            val name = cursor.getString(nameIndex)?.takeIf { it.isNotBlank() } ?: continue
            result.putIfAbsent(id, name)
        }
        return result
    }

    private fun readFirstValuePerId(cursor: Cursor, idColumn: String, valueColumn: String): Map<String, String> {
        val idIndex = cursor.getColumnIndex(idColumn)
        val valueIndex = cursor.getColumnIndex(valueColumn)
        if (idIndex < 0 || valueIndex < 0) return emptyMap()

        val result = LinkedHashMap<String, String>()
        while (cursor.moveToNext()) {
            val id = cursor.getString(idIndex)?.takeIf { it.isNotBlank() } ?: continue
            val value = cursor.getString(valueIndex)?.takeIf { it.isNotBlank() } ?: continue
            result.putIfAbsent(id, value)
        }
        return result
    }

    /**
     * Runs a content-resolver read that must not bring the caller down: covers the
     * permission being revoked between the check and the query (`SecurityException`) and
     * flaky provider failures, while still letting cancellation propagate.
     */
    private suspend fun <T> guarded(fallback: T, block: () -> T): T =
        runCatching { block() }.getOrElse { error ->
            currentCoroutineContext().ensureActive()
            if (error is CancellationException) throw error
            fallback
        }
}

/** Neutralises SQL `LIKE` wildcards in user input so a typed `%` matches a literal percent. */
private fun escapeForLike(raw: String): String = buildString(raw.length) {
    raw.forEach { char ->
        if (char == '\\' || char == '%' || char == '_') append('\\')
        append(char)
    }
}

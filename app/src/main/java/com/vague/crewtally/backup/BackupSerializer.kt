package com.vague.crewtally.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The one JSON encode/decode seam for backup payloads. [decode] never throws — a hand-edited or
 * corrupted file is an expected input here (LOCKED: "import validates before touching the DB"),
 * so a parse failure is reported as `null` for [BackupValidator] to turn into a clean, specific
 * message rather than letting a [SerializationException] surface as a stack trace to the user.
 */
object BackupSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(payload: BackupPayload): String = json.encodeToString(BackupPayload.serializer(), payload)

    fun decode(raw: String): BackupPayload? = try {
        json.decodeFromString(BackupPayload.serializer(), raw)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }
}

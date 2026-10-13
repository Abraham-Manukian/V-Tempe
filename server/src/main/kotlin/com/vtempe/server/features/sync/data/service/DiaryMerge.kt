package com.vtempe.server.features.sync.data.service

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** The merged diary would exceed [MAX_SYNC_PAYLOAD_CHARS]; nothing is written. */
class SyncPayloadTooLargeException : IllegalArgumentException("Merged sync payload too large")

/**
 * One device's changes applied to a stored diary (VT-AUD-057): [deletes] are removed, [upserts]
 * replace their keys, every other entry stays. A key changed on two devices keeps the change that
 * arrived last. A stored payload that is not a JSON object is unreadable for every client already,
 * so the diary starts over from the changes.
 */
internal fun mergeDiary(current: String?, upserts: Map<String, JsonElement>, deletes: Collection<String>): String {
    val stored = current?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    val merged = LinkedHashMap<String, JsonElement>(stored ?: emptyMap())
    deletes.forEach(merged::remove)
    merged.putAll(upserts)
    return JsonObject(merged).toString()
}

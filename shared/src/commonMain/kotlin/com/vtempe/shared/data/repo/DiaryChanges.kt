package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.dto.SyncChangesDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * What [local] changed since [base] (VT-AUD-057): entries added or changed, keys removed. With no
 * known base everything local is sent and nothing removed, so another device's entries survive.
 * Null when [local] is not a JSON object (then only a whole-snapshot upload can carry it).
 */
internal fun diaryChanges(base: String?, local: String): SyncChangesDto? {
    val now = local.asJsonObject() ?: return null
    val before = base?.asJsonObject() ?: JsonObject(emptyMap())
    return SyncChangesDto(
        upserts = now.filter { (key, value) -> before[key] != value },
        deletes = before.keys.filter { it !in now },
    )
}

private fun String.asJsonObject(): JsonObject? = runCatching { Json.parseToJsonElement(this) as? JsonObject }.getOrNull()

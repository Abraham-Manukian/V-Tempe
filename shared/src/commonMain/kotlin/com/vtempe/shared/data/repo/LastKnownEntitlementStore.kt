package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The last subscription status the server confirmed, per account — shown marked "not updated" offline. */
class LastKnownEntitlementStore(private val settings: Settings) {

    @Serializable
    data class Entry(val uid: String, val active: Boolean, val expiresAt: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    fun save(uid: String, active: Boolean, expiresAt: String?) {
        settings.putString(KEY, json.encodeToString(Entry.serializer(), Entry(uid, active, expiresAt)))
    }

    /** Only ever the entry of [uid] — another account's status is never shown. */
    fun load(uid: String): Entry? = settings.getStringOrNull(KEY)
        ?.let { runCatching { json.decodeFromString(Entry.serializer(), it) }.getOrNull() }
        ?.takeIf { it.uid == uid }

    fun clear() = settings.remove(KEY)

    private companion object {
        const val KEY = "entitlement.last_known.v1"
    }
}

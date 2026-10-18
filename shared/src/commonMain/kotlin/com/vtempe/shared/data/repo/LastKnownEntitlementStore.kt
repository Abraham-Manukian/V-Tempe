package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.repository.LastKnownEntitlement
import com.vtempe.shared.domain.repository.SubscriptionEntitlement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The last subscription status the server confirmed, per account — shown marked "not updated" offline. */
class LastKnownEntitlementStore(private val settings: Settings) : LastKnownEntitlement {

    @Serializable
    private data class Entry(val uid: String, val active: Boolean, val expiresAt: String? = null)

    private val json = Json { ignoreUnknownKeys = true }

    override fun save(uid: String, entitlement: SubscriptionEntitlement) {
        settings.putString(KEY, json.encodeToString(Entry.serializer(), Entry(uid, entitlement.active, entitlement.expiresAt)))
    }

    override fun load(uid: String): SubscriptionEntitlement? = settings.getStringOrNull(KEY)
        ?.let { runCatching { json.decodeFromString(Entry.serializer(), it) }.getOrNull() }
        ?.takeIf { it.uid == uid }
        ?.let { SubscriptionEntitlement(it.active, it.expiresAt) }

    fun clear() = settings.remove(KEY)

    private companion object {
        const val KEY = "entitlement.last_known.v1"
    }
}

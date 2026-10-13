package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.repository.SyncDomain

/**
 * Per diary domain, the snapshot this device last delivered to or restored from the server
 * (VT-AUD-057). Uploads send only what changed since it, so entries another device added are
 * never overwritten. Cleared with the device's user data.
 */
class SyncBaseStore(private val settings: Settings) {
    fun load(domain: SyncDomain): String? = settings.getStringOrNull(key(domain))

    fun save(domain: SyncDomain, payload: String) = settings.putString(key(domain), payload)

    fun clear() = SyncDomain.entries.forEach { settings.remove(key(it)) }

    private fun key(domain: SyncDomain) = "sync.base.v1.${domain.wireKey}"
}

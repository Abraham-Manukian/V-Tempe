package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.repository.SyncDomain

/**
 * Which sync domains hold local changes the server has not confirmed yet (VT-AUD-056), kept on
 * disk so an upload that failed offline is delivered after a restart without another edit.
 *
 * Per domain there are two counters: the revision of the last local change and the last
 * revision the server confirmed. A domain is pending while the first is ahead. A local change
 * (any thread) writes only the first, the uploader writes only the second — no key is written by
 * both, so a change made while an upload is in flight can never be erased by that upload's
 * confirmation; it simply stays ahead and goes out next.
 */
class PendingSyncStore(private val settings: Settings) {

    /** A local change to [domain] — call after the change is stored. */
    fun markChanged(domain: SyncDomain) {
        settings.putString(changedKey(domain), (changed(domain) + 1).toString())
    }

    /** The revision an upload made now would carry; read BEFORE reading the data to send. */
    fun currentRevision(domain: SyncDomain): Long = changed(domain)

    fun isPending(domain: SyncDomain): Boolean = changed(domain) > confirmed(domain)

    fun pendingDomains(): List<SyncDomain> = SyncDomain.entries.filter(::isPending)

    /** The server stored [domain] as it was at [revision]. */
    fun confirm(domain: SyncDomain, revision: Long) {
        if (revision > confirmed(domain)) settings.putString(confirmedKey(domain), revision.toString())
    }

    /** Device data was cleared or now belongs to another account: nothing of it is owed. */
    fun clear() {
        SyncDomain.entries.forEach {
            settings.remove(changedKey(it))
            settings.remove(confirmedKey(it))
        }
    }

    private fun changed(domain: SyncDomain): Long = settings.getStringOrNull(changedKey(domain))?.toLongOrNull() ?: 0L
    private fun confirmed(domain: SyncDomain): Long = settings.getStringOrNull(confirmedKey(domain))?.toLongOrNull() ?: 0L
    private fun changedKey(domain: SyncDomain) = "sync.pending.changed.v1.${domain.wireKey}"
    private fun confirmedKey(domain: SyncDomain) = "sync.pending.confirmed.v1.${domain.wireKey}"
}

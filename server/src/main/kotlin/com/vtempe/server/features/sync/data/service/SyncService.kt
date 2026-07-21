package com.vtempe.server.features.sync.data.service

import com.vtempe.server.features.sync.domain.port.SyncBlobRecord
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository

/** The domain names a client is allowed to sync — anything else is rejected before it reaches
 *  the repository, so this table can't be abused as an arbitrary per-user key-value store. Keep
 *  in sync with the client's SyncDomain enum (shared/.../domain/repository/Repositories.kt). */
val ALLOWED_SYNC_DOMAINS = setOf("profile", "workoutProgress", "sleep", "weight")

/** A generous cap, not a tuned one — these are compact JSON snapshots (a profile, a few weeks of
 *  workout completions, a couple months of sleep/weight entries), normally a few KB. 256 KB stops
 *  a buggy or malicious client from writing an unbounded blob, without being a real risk for any
 *  size this data legitimately reaches. */
const val MAX_SYNC_PAYLOAD_CHARS = 256 * 1024

class SyncService(private val repository: SyncBlobRepository) {
    suspend fun push(userId: String, domain: String, payload: String) {
        require(domain in ALLOWED_SYNC_DOMAINS) { "Unknown sync domain: $domain" }
        require(payload.length <= MAX_SYNC_PAYLOAD_CHARS) { "Sync payload too large" }
        repository.push(userId, domain, payload)
    }

    suspend fun pullAll(userId: String): Map<String, SyncBlobRecord> = repository.pullAll(userId)
}

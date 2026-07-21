package com.vtempe.server.features.sync.domain.port

import java.time.Instant

data class SyncBlobRecord(val payload: String, val updatedAt: Instant)

interface SyncBlobRepository {
    /** Overwrites the stored snapshot for (userId, domain) — last-write-wins, no merge. */
    suspend fun push(userId: String, domain: String, payload: String)

    /** Every domain currently stored for [userId], keyed by domain name. */
    suspend fun pullAll(userId: String): Map<String, SyncBlobRecord>
}

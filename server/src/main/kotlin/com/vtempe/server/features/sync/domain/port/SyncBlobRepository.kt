package com.vtempe.server.features.sync.domain.port

import java.time.Instant

data class SyncBlobRecord(val payload: String, val updatedAt: Instant)

interface SyncBlobRepository {
    /** Overwrites the stored snapshot for (userId, domain) — last-write-wins, no merge. Returns
     *  false and writes nothing while [deleteAll] blocks writes for [userId]. */
    suspend fun push(userId: String, domain: String, payload: String, now: Instant = Instant.now()): Boolean

    /** Every domain currently stored for [userId], keyed by domain name. */
    suspend fun pullAll(userId: String): Map<String, SyncBlobRecord>

    /**
     * Removes every domain stored for [userId] and refuses its pushes until [blockWritesUntil]
     * (VT-AUD-059). Atomic with [push]: a push of the same user either lands before and is
     * deleted, or comes after and is refused. A no-op apart from the barrier when nothing is stored.
     */
    suspend fun deleteAll(userId: String, now: Instant, blockWritesUntil: Instant)
}

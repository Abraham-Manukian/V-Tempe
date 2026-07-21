package com.vtempe.server.features.sync.data.repo

import com.vtempe.server.features.sync.domain.port.SyncBlobRecord
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** DI fallback when DATABASE_URL isn't configured — mirrors InMemoryEntitlementRepository. Data
 *  doesn't survive a restart; that's an accepted trade-off so a fresh checkout still boots. */
class InMemorySyncBlobRepository : SyncBlobRepository {
    private val blobs = ConcurrentHashMap<Pair<String, String>, SyncBlobRecord>()

    override suspend fun push(userId: String, domain: String, payload: String) {
        blobs[userId to domain] = SyncBlobRecord(payload, Instant.now())
    }

    override suspend fun pullAll(userId: String): Map<String, SyncBlobRecord> =
        blobs.entries
            .filter { it.key.first == userId }
            .associate { it.key.second to it.value }
}

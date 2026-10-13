package com.vtempe.server.features.sync.data.repo

import com.vtempe.server.features.sync.domain.port.SyncBlobRecord
import com.vtempe.server.features.sync.domain.port.SyncBlobRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/** DI fallback when DATABASE_URL isn't configured — mirrors InMemoryEntitlementRepository. Data
 *  doesn't survive a restart; that's an accepted trade-off so a fresh checkout still boots. */
class InMemorySyncBlobRepository : SyncBlobRepository {
    private val blobs = ConcurrentHashMap<Pair<String, String>, SyncBlobRecord>()
    private val barriers = HashMap<String, Instant>()
    // One lock for writes and deletions, so a barrier check and the write it allows are atomic.
    private val writes = Mutex()

    override suspend fun push(userId: String, domain: String, payload: String, now: Instant): Boolean =
        writes.withLock {
            if (barriers[userId]?.isAfter(now) == true) return@withLock false
            blobs[userId to domain] = SyncBlobRecord(payload, now)
            true
        }

    override suspend fun update(userId: String, domain: String, now: Instant, transform: (current: String?) -> String): Boolean =
        writes.withLock {
            if (barriers[userId]?.isAfter(now) == true) return@withLock false
            blobs[userId to domain] = SyncBlobRecord(transform(blobs[userId to domain]?.payload), now)
            true
        }

    override suspend fun pullAll(userId: String): Map<String, SyncBlobRecord> =
        blobs.entries
            .filter { it.key.first == userId }
            .associate { it.key.second to it.value }

    override suspend fun deleteAll(userId: String, now: Instant, blockWritesUntil: Instant) {
        writes.withLock {
            barriers.values.removeIf { !it.isAfter(now) }
            barriers[userId] = blockWritesUntil
            blobs.keys.removeIf { it.first == userId }
        }
    }
}

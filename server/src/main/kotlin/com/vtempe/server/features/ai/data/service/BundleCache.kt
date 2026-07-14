package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Coach-bundle cache + in-flight request dedup for [AiService], as an injectable Koin
 * singleton rather than a JVM-static `companion object`. AiService is itself already a Koin
 * `single`, so within one process this doesn't change runtime behavior — but a companion
 * object silently shares state with any AiService constructed outside Koin (e.g. in a test),
 * and hides what should be an explicit collaborator. This class is also the seam for a future
 * Redis/Memorystore-backed implementation if cross-instance (horizontal scaling) dedup ever
 * becomes worth the cost — see ARCHITECTURE_SECURITY_BACKLOG.md item N1.
 */
class BundleCache {
    companion object {
        const val BundleCacheTtlMs = 30 * 60 * 1000L
        const val FallbackCacheTtlMs = 2 * 60 * 1000L
    }

    data class PendingBundle(
        val deferred: CompletableDeferred<AiBootstrapResponse>,
        val isOwner: Boolean
    )

    private data class CacheEntry(
        val bundle: AiBootstrapResponse,
        val timestamp: Long,
        val ttlMs: Long
    )

    private val entries = ConcurrentHashMap<String, CacheEntry>()
    private val mutex = Mutex()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<AiBootstrapResponse>>()

    private fun isFresh(entry: CacheEntry, now: Long = System.currentTimeMillis()): Boolean =
        now - entry.timestamp <= entry.ttlMs

    suspend fun loadIfFresh(requestId: String): AiBootstrapResponse? {
        val now = System.currentTimeMillis()
        return mutex.withLock {
            entries[requestId]?.let { entry ->
                if (isFresh(entry, now)) {
                    entry.bundle
                } else {
                    entries.remove(requestId)
                    null
                }
            }
        }
    }

    /**
     * Returns a [PendingBundle] the caller can await. If nobody else is generating this
     * request, the caller becomes the owner ([PendingBundle.isOwner] = true) and must call
     * [store] once it has a result.
     */
    suspend fun lockInFlight(requestId: String): PendingBundle {
        return mutex.withLock {
            entries[requestId]?.let { entry ->
                if (isFresh(entry)) {
                    return@withLock PendingBundle(
                        deferred = CompletableDeferred<AiBootstrapResponse>().apply { complete(entry.bundle) },
                        isOwner = false
                    )
                }
                entries.remove(requestId)
            }

            val existing = inFlight[requestId]
            if (existing != null) {
                PendingBundle(deferred = existing, isOwner = false)
            } else {
                val created = CompletableDeferred<AiBootstrapResponse>()
                inFlight[requestId] = created
                PendingBundle(deferred = created, isOwner = true)
            }
        }
    }

    /** Stores [bundle] for [requestId] and completes any waiter registered via [lockInFlight]. */
    suspend fun store(requestId: String, bundle: AiBootstrapResponse, ttlMs: Long) {
        mutex.withLock {
            entries[requestId] = CacheEntry(bundle = bundle, timestamp = System.currentTimeMillis(), ttlMs = ttlMs)
            inFlight.remove(requestId)?.complete(bundle)
        }
    }

    /**
     * The owner MUST call this if it's giving up without calling [store] (e.g. rethrowing an
     * unexpected error instead of falling back). Without it, the in-flight [CompletableDeferred]
     * is never completed — every concurrent waiter from [lockInFlight], and every future request
     * for the same [requestId] until the process restarts, would join it and hang forever.
     */
    suspend fun fail(requestId: String, error: Throwable) {
        mutex.withLock {
            inFlight.remove(requestId)?.completeExceptionally(error)
        }
    }
}

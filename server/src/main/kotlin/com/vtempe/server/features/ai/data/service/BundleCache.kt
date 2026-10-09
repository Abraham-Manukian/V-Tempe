package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import kotlinx.coroutines.CompletableDeferred

/**
 * Coach-bundle cache + in-flight request dedup for [AiService], as an injectable Koin
 * singleton rather than a JVM-static `companion object`. This class is also the seam for a
 * future Redis/Memorystore-backed implementation if cross-instance dedup ever becomes worth the
 * cost — see ARCHITECTURE_SECURITY_BACKLOG.md item N1.
 *
 * Memory is bounded (VT-AUD-006): at most [maxEntries] finished bundles are kept (least recently
 * used evicted first), and expired ones are dropped on every write whether or not their key is
 * ever requested again. In-flight generations are never evicted.
 *
 * Every in-flight generation is finished exactly once (VT-AUD-007): [complete], [fail] or
 * [abandon] only touch the entry of THEIR generation (the deferred handed out by
 * [lockInFlight]), so a late cleanup can never remove a newer owner's entry. All operations are
 * short, non-suspending critical sections, so cleanup also runs while a coroutine is cancelled.
 */
class BundleCache(
    private val maxEntries: Int = 256,
    /** Monotonic milliseconds; tests pass a fake clock. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    companion object {
        const val BundleCacheTtlMs = 30 * 60 * 1000L
        const val FallbackCacheTtlMs = 2 * 60 * 1000L
    }

    data class PendingBundle(
        val deferred: CompletableDeferred<AiBootstrapResponse>,
        val isOwner: Boolean
    )

    /** Completes waiters of a generation whose owner was cancelled: they should start their own. */
    class GenerationAbandoned : IllegalStateException("bundle generation abandoned by its owner")

    private class CacheEntry(val bundle: AiBootstrapResponse, val expiresAtMs: Long)

    private val lock = Any()
    private val entries = object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>): Boolean = size > maxEntries
    }
    private val inFlight = HashMap<String, CompletableDeferred<AiBootstrapResponse>>()

    fun loadIfFresh(requestId: String): AiBootstrapResponse? = synchronized(lock) { freshEntry(requestId) }

    /**
     * Returns a [PendingBundle] the caller can await. If nobody else is generating this
     * request, the caller becomes the owner ([PendingBundle.isOwner] = true) and must finish it
     * with exactly one of [complete], [fail] or [abandon].
     */
    fun lockInFlight(requestId: String): PendingBundle = synchronized(lock) {
        freshEntry(requestId)?.let { cached ->
            return@synchronized PendingBundle(CompletableDeferred(cached), isOwner = false)
        }
        inFlight[requestId]?.let { return@synchronized PendingBundle(it, isOwner = false) }
        val created = CompletableDeferred<AiBootstrapResponse>()
        inFlight[requestId] = created
        PendingBundle(created, isOwner = true)
    }

    /** Caches [bundle] for [ttlMs] and hands it to every waiter of [generation]. */
    fun complete(requestId: String, generation: CompletableDeferred<AiBootstrapResponse>, bundle: AiBootstrapResponse, ttlMs: Long) {
        synchronized(lock) {
            val now = clock()
            entries.values.removeIf { it.expiresAtMs <= now }
            entries[requestId] = CacheEntry(bundle, now + ttlMs)
            inFlight.remove(requestId, generation)
        }
        generation.complete(bundle)
    }

    /** Ends [generation] with [error]; its waiters receive the same error. */
    fun fail(requestId: String, generation: CompletableDeferred<AiBootstrapResponse>, error: Throwable) {
        synchronized(lock) { inFlight.remove(requestId, generation) }
        generation.completeExceptionally(error)
    }

    /** Ends [generation] because its owner was cancelled; waiters take over (see [GenerationAbandoned]). */
    fun abandon(requestId: String, generation: CompletableDeferred<AiBootstrapResponse>) =
        fail(requestId, generation, GenerationAbandoned())

    internal fun cachedCount(): Int = synchronized(lock) { entries.size }
    internal fun inFlightCount(): Int = synchronized(lock) { inFlight.size }

    private fun freshEntry(requestId: String): AiBootstrapResponse? {
        val entry = entries[requestId] ?: return null
        if (entry.expiresAtMs > clock()) return entry.bundle
        entries.remove(requestId)
        return null
    }
}

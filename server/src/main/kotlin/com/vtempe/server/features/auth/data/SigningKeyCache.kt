package com.vtempe.server.features.auth.data

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.concurrent.locks.ReentrantLock

/**
 * Google's signing keys, held as one key set instead of per key id.
 *
 * The key id comes from a token that is not verified yet, so it is attacker-chosen. A per-id cache
 * in front of a rate-limited fetch (jwks-rsa's `cached().rateLimited()`) lets anyone spend the
 * fetch budget with random ids; once the real keys age out of the cache, every genuine token fails.
 * Here an unknown id costs at most one refetch per [minRefetchGap] for everybody together, and a
 * failed or empty refetch never drops the keys already known.
 *
 * Once keys are known, lookups never wait for a download: a known key is served from the current
 * snapshot, stale or not, and only one caller at a time fetches (single flight). The others go on
 * with the snapshot they have, so a slow key endpoint cannot stall every token check behind it.
 * Only before the first successful fetch (no keys at all) do callers wait for it, so the requests
 * of a fresh instance are not all refused while one of them downloads the keys.
 */
class SigningKeyCache(
    /** Downloads the whole published key set (blocking; called on an IO thread). */
    private val fetchAll: () -> List<Jwk>,
    private val refreshAfter: Duration = Duration.ofHours(6),
    private val minRefetchGap: Duration = Duration.ofMinutes(1),
    private val clock: () -> Instant = Instant::now,
) : JwkProvider {
    private val logger = LoggerFactory.getLogger(SigningKeyCache::class.java)

    private class Snapshot(val keys: Map<String, Jwk>, val fetchedAt: Instant?)

    @Volatile private var snapshot = Snapshot(emptyMap(), fetchedAt = null)
    @Volatile private var lastAttempt: Instant? = null
    private val fetching = ReentrantLock()

    override fun get(keyId: String?): Jwk {
        if (keyId == null) throw SigningKeyNotFoundException("token has no key id", null)
        val current = snapshot
        val now = clock()
        val stale = current.fetchedAt?.let { now.isAfter(it.plus(refreshAfter)) } ?: true
        if (stale || keyId !in current.keys) {
            if (current.keys.isEmpty()) awaitFirstFetch(now) else if (mayFetch(now)) refreshIfNobodyElseIs(now)
        }
        return snapshot.keys[keyId] ?: throw SigningKeyNotFoundException("no published key $keyId", null)
    }

    private fun mayFetch(now: Instant): Boolean = lastAttempt?.let { !now.isBefore(it.plus(minRefetchGap)) } ?: true

    /** No keys yet: wait for the fetch under way (or make it), so a fresh instance's first
     *  requests are not all refused while one of them downloads the keys. */
    private fun awaitFirstFetch(now: Instant) {
        fetching.lock()
        try {
            if (snapshot.keys.isEmpty() && mayFetch(now)) fetch(now)
        } finally {
            fetching.unlock()
        }
    }

    /** Single flight: a caller that finds a fetch under way goes on with the snapshot it has. */
    private fun refreshIfNobodyElseIs(now: Instant) {
        if (!fetching.tryLock()) return
        try {
            if (mayFetch(now)) fetch(now) // another caller may have fetched while this one was checking
        } finally {
            fetching.unlock()
        }
    }

    /** Called holding [fetching]. */
    private fun fetch(now: Instant) {
        lastAttempt = now
        val fetched = try {
            fetchAll().filter { it.id != null }.associateBy { it.id }
        } catch (e: Exception) {
            logger.warn("Could not refresh Firebase signing keys, keeping {} known keys: {}", snapshot.keys.size, e.message)
            return
        }
        if (fetched.isEmpty()) {
            logger.warn("Firebase signing key set came back empty, keeping {} known keys", snapshot.keys.size)
            return
        }
        snapshot = Snapshot(fetched, fetchedAt = now)
    }
}

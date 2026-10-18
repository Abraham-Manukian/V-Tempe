package com.vtempe.server

import com.auth0.jwk.Jwk
import com.auth0.jwk.SigningKeyNotFoundException
import com.vtempe.server.features.auth.data.SigningKeyCache
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Forged key ids must not be able to lock genuine tokens out (see SigningKeyCache). */
class SigningKeyCacheTest {
    private var now = Instant.parse("2026-01-01T00:00:00Z")
    private var published = listOf(jwk("k1"))
    private var fail = false
    private var fetches = 0
    private val cache = SigningKeyCache(
        fetchAll = { fetches++; if (fail) error("endpoint down") else published },
        refreshAfter = Duration.ofHours(6),
        minRefetchGap = Duration.ofMinutes(1),
        clock = { now },
    )

    @Test
    fun `a stream of unknown key ids refetches at most once a minute`() {
        cache.get("k1")
        repeat(500) { i ->
            now = now.plusMillis(100) // 50 seconds in total
            assertThrows<SigningKeyNotFoundException> { cache.get("forged-$i") }
        }

        assertEquals(1, fetches, "only the first fetch; every forged id inside the minute is refused without one")
    }

    @Test
    fun `forged key ids never cost a genuine token its key`() {
        cache.get("k1")
        repeat(100) { i ->
            now = now.plusSeconds(61)
            runCatching { cache.get("forged-$i") }
        }

        assertEquals("k1", cache.get("k1").id)
    }

    @Test
    fun `a rotated-in key is picked up on its first use`() {
        cache.get("k1")
        published = listOf(jwk("k1"), jwk("k2"))
        now = now.plusSeconds(61)

        assertEquals("k2", cache.get("k2").id)
    }

    @Test
    fun `a failed or empty refresh keeps the known keys`() {
        cache.get("k1")
        now = now.plus(Duration.ofHours(7))
        fail = true
        assertEquals("k1", cache.get("k1").id)

        fail = false
        published = emptyList()
        now = now.plusSeconds(61)
        assertEquals("k1", cache.get("k1").id)
    }

    @Test
    fun `stale keys are refreshed after the refresh interval`() {
        cache.get("k1")
        published = listOf(jwk("k3"))
        now = now.plus(Duration.ofHours(7))

        assertEquals("k3", cache.get("k3").id)
        assertThrows<SigningKeyNotFoundException> { cache.get("k1") }
        assertEquals(2, fetches)
    }

    @Test
    fun `a token without a key id is refused without a fetch`() {
        assertThrows<SigningKeyNotFoundException> { cache.get(null) }
        assertEquals(0, fetches)
    }

    @Test
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `a known key is served while another caller is stuck in a refresh`() {
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        var hang = false
        val slow = SigningKeyCache(
            fetchAll = { if (hang) { started.countDown(); release.await() }; published },
            clock = { now },
        )
        slow.get("k1")
        now = now.plus(Duration.ofHours(7)) // stale: the next lookup starts a refresh
        hang = true
        val pool = Executors.newSingleThreadExecutor()
        try {
            pool.submit { slow.get("k1") }
            assertTrue(started.await(5, TimeUnit.SECONDS))

            assertEquals("k1", slow.get("k1").id, "served from the snapshot, not queued behind the download")
        } finally {
            release.countDown()
            pool.shutdown()
        }
    }

    @Test
    fun `before the first fetch, concurrent callers wait for it instead of being refused`() {
        val gate = CountDownLatch(1)
        val cold = SigningKeyCache(fetchAll = { fetches++; gate.await(); published }, clock = { now })
        val pool = Executors.newFixedThreadPool(4)
        try {
            val results = List(4) { pool.submit<String> { cold.get("k1").id } }
            Thread.sleep(100) // let all four reach the cache
            gate.countDown()

            assertEquals(List(4) { "k1" }, results.map { it.get(5, TimeUnit.SECONDS) })
            assertEquals(1, fetches)
        } finally {
            gate.countDown()
            pool.shutdown()
        }
    }

    private fun jwk(kid: String): Jwk = Jwk.fromValues(mapOf("kid" to kid, "kty" to "RSA", "alg" to "RS256", "n" to "AQAB", "e" to "AQAB"))
}

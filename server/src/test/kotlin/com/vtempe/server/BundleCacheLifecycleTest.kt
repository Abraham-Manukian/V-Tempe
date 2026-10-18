package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmException
import com.vtempe.server.features.ai.data.llm.LlmRepairer
import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import com.vtempe.server.features.ai.data.llm.pipeline.PipelineConfig
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.schema.ResponseSchema
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.AiService
import com.vtempe.server.features.ai.data.service.BundleCache
import com.vtempe.server.features.ai.data.service.NoSafeNutritionPlanException
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapRequest
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

/** VT-AUD-006 (bounded, expiring cache) and VT-AUD-007 (every generation is finished once). */
class BundleCacheLifecycleTest {

    // ── VT-AUD-006: the cache itself, fake clock ─────────────────────────────

    private var now = 0L
    private fun cache(max: Int = 3) = BundleCache(maxEntries = max, clock = { now })
    private fun bundle() = AiBootstrapResponse()

    private fun BundleCache.put(key: String, ttl: Long = BundleCache.BundleCacheTtlMs): AiBootstrapResponse {
        val pending = lockInFlight(key)
        val value = bundle()
        complete(key, pending.deferred, value, ttl)
        return value
    }

    @Test
    fun `a stream of unique keys never exceeds the capacity`() {
        val c = cache(max = 3)
        repeat(100) { c.put("k$it") }
        assertEquals(3, c.cachedCount())
        assertNotNull(c.loadIfFresh("k99"))
        assertNull(c.loadIfFresh("k0"))
    }

    @Test
    fun `expired entries are released without ever being read again`() {
        val c = cache(max = 100)
        repeat(10) { c.put("old$it", ttl = 1_000) }
        now += 1_001
        c.put("new")
        assertEquals(1, c.cachedCount(), "the 10 expired, never re-read entries are gone")
    }

    @Test
    fun `fresh hits, the fallback TTL and recently used entries behave`() {
        val c = cache(max = 2)
        val a = c.put("a")
        val f = c.put("fallback", ttl = BundleCache.FallbackCacheTtlMs)
        assertSame(a, c.loadIfFresh("a")) // "a" becomes most recently used
        c.put("b")                         // evicts "fallback", the least recently used
        assertNull(c.loadIfFresh("fallback"))
        assertSame(a, c.loadIfFresh("a"))
        now += BundleCache.FallbackCacheTtlMs + 1
        assertSame(a, c.loadIfFresh("a"), "a normal bundle outlives the fallback TTL")
        assertTrue(f !== a)
    }

    @Test
    fun `a stale cleanup cannot remove a newer owner's generation`() {
        val c = cache()
        val first = c.lockInFlight("k")
        c.fail("k", first.deferred, IllegalStateException("first failed"))
        val second = c.lockInFlight("k")
        assertTrue(second.isOwner)
        c.fail("k", first.deferred, IllegalStateException("late duplicate cleanup")) // must not touch `second`
        val waiter = c.lockInFlight("k")
        assertSame(second.deferred, waiter.deferred, "the newer generation is still the one being waited for")
        assertEquals(1, c.inFlightCount())
    }

    @Test
    fun `in-flight generations are never evicted by capacity`() {
        val c = cache(max = 1)
        val pending = c.lockInFlight("generating")
        repeat(5) { c.put("other$it") }
        assertSame(pending.deferred, c.lockInFlight("generating").deferred)
    }

    // ── VT-AUD-007: through the real AiService ───────────────────────────────

    private class GatedFailingLlm : LLMClient {
        val calls = AtomicInteger()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun generateJson(prompt: String, schema: ResponseSchema?): String {
            calls.incrementAndGet()
            gate?.await()
            throw LlmException.Timeout("fake provider timeout")
        }
    }

    private fun service(llm: LLMClient, cache: BundleCache): AiService {
        val pipeline = LlmPipeline(
            PipelineConfig(), ResponseExtractor(), JsonSanitizer(), Decoder(Json { ignoreUnknownKeys = true }),
            FeedbackComposer(), LlmRawStore(enabled = false), LlmErrorTracker()
        )
        val catalog = BuiltInExerciseCatalog()
        return AiService(llm, llm, LlmRepairer(pipeline), catalog, DefaultTrainingPlanResolver(catalog), cache)
    }

    private val profile = AiProfile(
        age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 2,
        weeklySchedule = mapOf("Mon" to true, "Wed" to true, "Fri" to true),
    )

    @Test
    fun `a failure outside the LLM call frees the key for the next request`() = runBlocking {
        // Allergies that leave no fallback meal: the fallback path itself throws.
        val everything = listOf(
            "oats", "milk", "blueberries", "honey", "chicken", "rice", "broccoli", "carrots", "yogurt",
            "walnuts", "salmon", "quinoa", "spinach", "lemon", "vegetables", "fruit", "chickpeas", "pumpkin"
        )
        val cache = BundleCache()
        val ai = service(GatedFailingLlm(), cache)
        val request = AiBootstrapRequest(profile.copy(allergies = everything), 0, "en")

        repeat(2) {
            // Before the fix the second call waited on the poisoned entry; the timeout turns that into a failure.
            val error = runCatching { withTimeout(30_000) { ai.bundle(request) } }.exceptionOrNull()
            assertTrue(error is NoSafeNutritionPlanException, "expected a refusal, got $error")
            assertEquals(0, cache.inFlightCount())
        }
    }

    @Test
    fun `concurrent identical requests share one generation`() = runBlocking {
        val single = GatedFailingLlm()
        service(single, BundleCache()).bundle(AiBootstrapRequest(profile, 0, "en"))
        val callsForOne = single.calls.get()

        val llm = GatedFailingLlm().apply { gate = CompletableDeferred() }
        val cache = BundleCache()
        val ai = service(llm, cache)
        val results = List(3) { async { ai.bundle(AiBootstrapRequest(profile, 0, "en")) } }
        delay(200)
        llm.gate!!.complete(Unit)
        val bundles = results.awaitAll()
        assertTrue(bundles.all { it === bundles.first() }, "everyone gets the one generated bundle")
        assertEquals(callsForOne, llm.calls.get(), "no duplicate LLM work")
        assertEquals(0, cache.inFlightCount())
    }

    @Test
    fun `when the owner is cancelled a waiter generates instead`() = runBlocking {
        val llm = GatedFailingLlm().apply { gate = CompletableDeferred() }
        val cache = BundleCache()
        val ai = service(llm, cache)
        val owner = async { ai.bundle(AiBootstrapRequest(profile, 0, "en")) }
        delay(200)
        val waiter = async { ai.bundle(AiBootstrapRequest(profile, 0, "en")) }
        delay(200)
        owner.cancel()
        llm.gate!!.complete(Unit)
        val result = withTimeout(30_000) { waiter.await() }
        assertNotNull(result.trainingPlan)
        assertEquals(0, cache.inFlightCount())
    }

    @Test
    fun `a bundle with a section that fell back is cached only as long as a fallback`() {
        // Under load (a full LLM queue) a section is replaced by its template; such a bundle must
        // not stand in for the coach's answer for the next 30 minutes.
        assertEquals(BundleCache.FallbackCacheTtlMs, BundleCache.ttlFor(anySectionFellBack = true))
        assertEquals(BundleCache.BundleCacheTtlMs, BundleCache.ttlFor(anySectionFellBack = false))
    }
}

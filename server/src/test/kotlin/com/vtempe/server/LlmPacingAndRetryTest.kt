package com.vtempe.server

import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.features.ai.data.llm.LlmException
import com.vtempe.server.features.ai.data.llm.RetryingLLMClient
import com.vtempe.server.features.ai.data.llm.ThrottledLLMClient
import com.vtempe.server.features.ai.data.llm.schema.ResponseSchema
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** VT-AUD-008 (pacing from real starts, bounded queue) and VT-AUD-009 (Retry-After honoured).
 *  Virtual time only — no real sleeps, no provider. */
@OptIn(ExperimentalCoroutinesApi::class)
class LlmPacingAndRetryTest {

    private class Recording(private val clock: () -> Long, private val failures: MutableList<Throwable> = mutableListOf()) : LLMClient {
        val starts = mutableListOf<Long>()
        fun failNext(vararg errors: Throwable) = apply { failures += errors }
        override suspend fun generateJson(prompt: String, schema: ResponseSchema?): String {
            starts += clock()
            if (failures.isNotEmpty()) throw failures.removeAt(0)
            return "{}"
        }
    }

    private fun TestScope.vclock(): () -> Long = { currentTime }

    // ── VT-AUD-008 ─────────────────────────────────────────────────────────────

    @Test
    fun `a waiter cancelled before starting leaves no delay behind`() = runTest {
        val delegate = Recording(vclock())
        val client = ThrottledLLMClient(delegate, minSpacingMs = 60_000, clock = vclock())

        client.generateJson("first", null)                 // starts at t=0
        val cancelled = launch { client.generateJson("second", null) }
        runCurrent()
        cancelled.cancel()                                  // waits for its slot, then gives up
        val third = async { client.generateJson("third", null) }

        advanceTimeBy(60_001)
        assertEquals(listOf(0L, 60_000L), delegate.starts, "third starts one interval after the first real start")
        third.await()
    }

    @Test
    fun `many cancellations in a row do not push live requests out`() = runTest {
        val delegate = Recording(vclock())
        val client = ThrottledLLMClient(delegate, minSpacingMs = 10_000, clock = vclock())
        client.generateJson("first", null)
        val doomed = List(5) { launch { client.generateJson("doomed $it", null) } }
        runCurrent()
        doomed.forEach { it.cancel() }
        val live = async { client.generateJson("live", null) }
        advanceTimeBy(10_001)
        assertEquals(listOf(0L, 10_000L), delegate.starts)
        live.await()
    }

    @Test
    fun `live requests keep the minimum spacing between starts`() = runTest {
        val delegate = Recording(vclock())
        val client = ThrottledLLMClient(delegate, minSpacingMs = 2_500, clock = vclock())
        List(4) { async { client.generateJson("r$it", null) } }.awaitAll()
        assertEquals(listOf(0L, 2_500L, 5_000L, 7_500L), delegate.starts)
    }

    @Test
    fun `the queue is bounded`() = runTest {
        val delegate = Recording(vclock())
        val client = ThrottledLLMClient(delegate, minSpacingMs = 10_000, maxWaiting = 2, clock = vclock())
        client.generateJson("first", null)
        val queued = List(2) { launch { client.generateJson("q$it", null) } }
        runCurrent()
        assertThrows(LlmException.RateLimited::class.java) { kotlinx.coroutines.runBlocking { client.generateJson("overflow", null) } }
        queued.forEach { it.cancel() }
    }

    // ── VT-AUD-009 ─────────────────────────────────────────────────────────────

    private fun TestScope.retrying(delegate: LLMClient, attempts: Int = 2) = RetryingLLMClient(
        delegate, attempts = attempts, initialDelayMs = 2_500, maxDelayMs = 15_000, backoffMultiplier = 1.8,
        maxCooldownWaitMs = 30_000, clock = vclock(),
    )

    @Test
    fun `retry waits the full Retry-After even when it exceeds the backoff cap`() = runTest {
        val delegate = Recording(vclock()).failNext(LlmException.RateLimited("429", retryAfterMillis = 25_000))
        retrying(delegate).generateJson("p", null)
        assertEquals(listOf(0L, 25_000L), delegate.starts)
    }

    @Test
    fun `a Retry-After longer than a request can wait fails fast instead of retrying early`() = runTest {
        val delegate = Recording(vclock()).failNext(LlmException.RateLimited("429", retryAfterMillis = 120_000))
        val client = retrying(delegate)
        assertThrows(LlmException.RateLimited::class.java) { kotlinx.coroutines.runBlocking { client.generateJson("p", null) } }
        assertEquals(listOf(0L), delegate.starts, "no second call to the limited provider")

        // Other requests through the same client respect the shared cooldown and fail fast too.
        val other = runCatching { client.generateJson("other", null) }
        assertTrue(other.exceptionOrNull() is LlmException.RateLimited)
        assertEquals(1, delegate.starts.size)
    }

    @Test
    fun `a short Retry-After delays concurrent requests until the cooldown ends`() = runTest {
        val delegate = Recording(vclock()).failNext(LlmException.RateLimited("429", retryAfterMillis = 20_000))
        val client = retrying(delegate)
        val first = async { client.generateJson("first", null) }
        runCurrent()
        val second = async { client.generateJson("second", null) }
        awaitAll(first, second)
        assertTrue(delegate.starts.drop(1).all { it >= 20_000L }, delegate.starts.toString())
    }

    @Test
    fun `without a hint the capped backoff applies, and attempts are bounded`() = runTest {
        val delegate = Recording(vclock()).failNext(LlmException.RateLimited("429"), LlmException.RateLimited("429"))
        assertThrows(LlmException.RateLimited::class.java) {
            kotlinx.coroutines.runBlocking { RetryingLLMClient(delegate, attempts = 2, initialDelayMs = 0, clock = { 0L }).generateJson("p", null) }
        }
        assertEquals(2, delegate.starts.size)

        val ok = Recording(vclock()).failNext(LlmException.Timeout("t"))
        retrying(ok).generateJson("p", null)
        assertEquals(listOf(0L, 2_500L), ok.starts)
    }

    @Test
    fun `cancellation is never retried`() = runTest {
        val delegate = Recording(vclock()).failNext(CancellationException("cancelled"))
        assertThrows(CancellationException::class.java) {
            kotlinx.coroutines.runBlocking { retrying(delegate, attempts = 3).generateJson("p", null) }
        }
        assertEquals(1, delegate.starts.size)

        val slow = object : LLMClient {
            var calls = 0
            override suspend fun generateJson(prompt: String, schema: ResponseSchema?): String {
                calls++
                kotlinx.coroutines.delay(60_000)
                return "{}"
            }
        }
        runCatching { withTimeout(1_000) { retrying(slow, attempts = 3).generateJson("p", null) } }
        assertEquals(1, slow.calls, "an outer timeout is not retried")
    }
}

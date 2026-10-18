package com.vtempe.server.features.ai.data.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/**
 * Paces calls to the underlying LLM client: two calls never START closer than [minSpacingMs]
 * apart, to stay under provider rate limits when many requests share this client.
 *
 * Pacing is measured from calls that actually started (VT-AUD-008). Callers wait their turn in a
 * fair queue and only the one that proceeds records its start, so a caller cancelled while
 * waiting leaves no phantom future slot behind — previously each waiter reserved a slot before
 * sleeping, and cancelled waiters kept pushing everyone else's start further out. Calls run
 * concurrently once started; only their starts are spaced. At most [maxWaiting] callers queue;
 * beyond that the call fails fast as [LlmException.RateLimited] so the caller can fall back.
 */
class ThrottledLLMClient(
    private val delegate: LLMClient,
    private val minSpacingMs: Long = 1500L,
    private val maxWaiting: Int = 32,
    /** Monotonic milliseconds; tests pass the virtual clock of their scheduler. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : LLMClient {
    private val turn = Mutex()
    private val waiting = AtomicInteger(0)
    private var lastStartMs: Long? = null

    override suspend fun generateJson(prompt: String, schema: com.vtempe.server.features.ai.data.llm.schema.ResponseSchema?): String {
        if (waiting.incrementAndGet() > maxWaiting) {
            waiting.decrementAndGet()
            throw LlmException.RateLimited("Local LLM queue is full ($maxWaiting waiting)", local = true)
        }
        try {
            turn.withLock {
                val last = lastStartMs
                if (last != null) {
                    val wait = minSpacingMs - (clock() - last)
                    if (wait > 0) delay(wait) // cancellation here releases the turn, nothing recorded
                }
                lastStartMs = clock()
            }
        } finally {
            waiting.decrementAndGet()
        }
        return delegate.generateJson(prompt, schema)
    }
}

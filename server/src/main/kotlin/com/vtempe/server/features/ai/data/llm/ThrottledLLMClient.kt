package com.vtempe.server.features.ai.data.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serialises access to the underlying LLM client and enforces a minimum delay between requests.
 * This helps avoid hitting provider rate limits when multiple coroutines invoke the client in parallel.
 */
class ThrottledLLMClient(
    private val delegate: LLMClient,
    private val minSpacingMs: Long = 1500L
) : LLMClient {
    private val mutex = Mutex()
    private var lastInvocationTimeMs: Long = 0L

    override suspend fun generateJson(prompt: String, schema: com.vtempe.server.features.ai.data.llm.schema.ResponseSchema?): String {
        val waitFor = mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastInvocationTimeMs
            val delayNeeded = minSpacingMs - elapsed
            val wait = if (delayNeeded > 0) delayNeeded else 0L
            lastInvocationTimeMs = now + wait
            wait
        }
        if (waitFor > 0) {
            delay(waitFor)
        }
        return delegate.generateJson(prompt, schema)
    }
}


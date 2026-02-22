package com.vtempe.server.features.ai.data.llm

import kotlin.math.max
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

class RetryingLLMClient(
    private val delegate: LLMClient,
    private val attempts: Int = 3,
    private val initialDelayMs: Long = 1_500,
    private val maxDelayMs: Long = 12_000,
    private val backoffMultiplier: Double = 2.0
) : LLMClient {
    override suspend fun generateJson(prompt: String): String {
        var attempt = 1
        var nextDelay = initialDelayMs.coerceAtLeast(0)
        var lastError: Throwable? = null

        while (attempt <= attempts) {
            try {
                return delegate.generateJson(prompt)
            } catch (ex: Throwable) {
                lastError = ex
                val retryable = isRetryable(ex)
                if (attempt >= attempts || !retryable) throw ex

                val hintedDelay = (ex as? RateLimitException)
                    ?.retryAfterMillis
                    ?.takeIf { it > 0 }
                    ?.coerceAtMost(maxDelayMs)
                val waitFor = (hintedDelay ?: nextDelay).coerceAtMost(maxDelayMs).coerceAtLeast(0)
                logger.warn(
                    "Retrying LLM after failure (attempt {}/{}). Waiting {} ms. reason={}",
                    attempt,
                    attempts,
                    waitFor,
                    ex.message ?: ex::class.simpleName
                )
                delay(waitFor)
                nextDelay = (max(nextDelay.toDouble() * backoffMultiplier, initialDelayMs.toDouble()))
                    .toLong()
                    .coerceAtMost(maxDelayMs)
                attempt += 1
            }
        }
        throw lastError ?: IllegalStateException("LLM request failed after $attempts attempt(s)")
    }

    private fun isRetryable(error: Throwable): Boolean {
        if (error is RateLimitException) return true
        return errorChain(error).any { cause ->
            val message = cause.message?.lowercase().orEmpty()
            if (message.contains("timed out") || message.contains("timeout")) return@any true
            val status = parseOpenRouterStatus(cause.message)
            status != null && status in RETRYABLE_HTTP_STATUSES
        }
    }

    private fun errorChain(error: Throwable): Sequence<Throwable> =
        generateSequence(error) { it.cause }

    private fun parseOpenRouterStatus(message: String?): Int? {
        if (message.isNullOrBlank()) return null
        val match = OPENROUTER_HTTP_REGEX.find(message) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RetryingLLMClient::class.java)
        private val OPENROUTER_HTTP_REGEX = Regex("""OpenRouter\s+(?:HTTP|error)\s+(\d{3})""", RegexOption.IGNORE_CASE)
        private val RETRYABLE_HTTP_STATUSES = setOf(408, 409, 425, 429, 500, 502, 503, 504)
    }
}


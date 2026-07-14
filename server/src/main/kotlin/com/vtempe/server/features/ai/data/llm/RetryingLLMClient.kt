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

                val hintedDelay = (ex as? LlmException.RateLimited)
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

    private fun isRetryable(error: Throwable): Boolean = errorChain(error).any { cause ->
        when (cause) {
            is LlmException.RateLimited, is LlmException.Timeout -> true
            is LlmException.Auth, is LlmException.PaymentRequired -> false
            // Unlike OpenRouterLLMClient.shouldTryNextModel (which can still usefully try a
            // DIFFERENT model on a null-status provider error), retrying the SAME request here
            // can't fix a deterministic relayed failure like "model not found" — only retry
            // when we actually know the status and it's one of the transient ones.
            is LlmException.Provider -> cause.status in RETRYABLE_HTTP_STATUSES
            else -> {
                // Legacy fallback for anything not covered by LlmException.
                val message = cause.message?.lowercase().orEmpty()
                message.contains("timed out") || message.contains("timeout")
            }
        }
    }

    private fun errorChain(error: Throwable): Sequence<Throwable> =
        generateSequence(error) { it.cause }

    companion object {
        private val logger = LoggerFactory.getLogger(RetryingLLMClient::class.java)
        private val RETRYABLE_HTTP_STATUSES = setOf(408, 409, 425, 429, 500, 502, 503, 504)
    }
}


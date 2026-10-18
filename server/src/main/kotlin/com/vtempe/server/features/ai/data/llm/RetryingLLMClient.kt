package com.vtempe.server.features.ai.data.llm

import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.max
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory

/**
 * Retries transient LLM failures with exponential backoff, and honours the provider's
 * Retry-After (VT-AUD-009).
 *
 * The local backoff cap [maxDelayMs] only shapes OUR retry spacing; it never shortens a cooldown
 * the provider asked for — that used to cut a 120 s Retry-After to 15 s and hit the still-limited
 * provider again. A Retry-After becomes a cooldown shared by every request through this client:
 * nobody calls the provider before it ends. When the cooldown is longer than [maxCooldownWaitMs]
 * (what a request can afford to wait), the call fails fast as [LlmException.RateLimited] so the
 * caller's fallback runs instead of a retry that is either too early or too late.
 */
class RetryingLLMClient(
    private val delegate: LLMClient,
    private val attempts: Int = 3,
    private val initialDelayMs: Long = 1_500,
    private val maxDelayMs: Long = 12_000,
    private val backoffMultiplier: Double = 2.0,
    private val maxCooldownWaitMs: Long = 30_000,
    /** Longest cooldown a Retry-After can impose: a bogus `Retry-After: 86400` must not switch the
     *  AI off on this instance for a day. */
    private val maxCooldownMs: Long = 5 * 60_000,
    /** Monotonic milliseconds; tests pass the virtual clock of their scheduler. */
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) : LLMClient {
    /** End of the provider-requested cooldown on [clock]; null when none was requested. */
    @Volatile
    private var cooldownUntilMs: Long? = null

    override suspend fun generateJson(prompt: String, schema: com.vtempe.server.features.ai.data.llm.schema.ResponseSchema?): String {
        var attempt = 1
        var nextDelay = initialDelayMs.coerceAtLeast(0)

        while (true) {
            awaitCooldown()
            try {
                return delegate.generateJson(prompt, schema)
            } catch (ex: CancellationException) {
                throw ex // cancellation (including an outer timeout) is never retried
            } catch (ex: Throwable) {
                val retryAfter = (ex as? LlmException.RateLimited)?.retryAfterMillis?.takeIf { it > 0 }?.coerceAtMost(maxCooldownMs)
                if (retryAfter != null) {
                    // Recorded before the attempt limit: the last attempt's 429 must still hold
                    // the next requests back.
                    val until = clock() + retryAfter
                    cooldownUntilMs = cooldownUntilMs?.let { max(it, until) } ?: until
                }
                if (attempt >= attempts || !isRetryable(ex)) throw ex

                if (retryAfter != null) {
                    if (retryAfter > maxCooldownWaitMs) throw ex // too long to wait: let the caller fall back
                    logger.warn("LLM rate-limited (attempt {}/{}); provider asked to wait {} ms", attempt, attempts, retryAfter)
                } else {
                    val waitFor = nextDelay.coerceIn(0, maxDelayMs)
                    logger.warn(
                        "Retrying LLM after failure (attempt {}/{}). Waiting {} ms. reason={}",
                        attempt, attempts, waitFor, ex.message ?: ex::class.simpleName
                    )
                    delay(waitFor)
                    nextDelay = max(nextDelay.toDouble() * backoffMultiplier, initialDelayMs.toDouble()).toLong().coerceAtMost(maxDelayMs)
                }
                attempt += 1
            }
        }
    }

    /** Waits out the provider's cooldown, or fails fast when it is longer than a request can wait. */
    private suspend fun awaitCooldown() {
        val remaining = (cooldownUntilMs ?: return) - clock()
        if (remaining <= 0) return
        if (remaining > maxCooldownWaitMs) {
            throw LlmException.RateLimited("Provider cooldown: ${remaining} ms left", retryAfterMillis = remaining)
        }
        delay(remaining)
    }

    private fun isRetryable(error: Throwable): Boolean = errorChain(error).any { cause ->
        when (cause) {
            // The local queue's refusal fails fast so the caller falls back (ThrottledLLMClient).
            is LlmException.RateLimited -> !cause.local
            is LlmException.Timeout -> true
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

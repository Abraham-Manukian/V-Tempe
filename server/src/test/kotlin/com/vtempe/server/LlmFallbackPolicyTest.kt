package com.vtempe.server

import com.vtempe.server.features.ai.data.llm.LlmException
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipelineExhaustedException
import com.vtempe.server.features.ai.data.service.isExpectedLlmFailure
import com.vtempe.server.features.ai.data.service.shouldFallbackToFree
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LlmFallbackPolicyTest {

    @Test
    fun `rate limited falls back to free`() {
        assertTrue(shouldFallbackToFree(LlmException.RateLimited("429")))
    }

    @Test
    fun `payment required falls back to free`() {
        assertTrue(shouldFallbackToFree(LlmException.PaymentRequired("402")))
    }

    @Test
    fun `auth error falls back to free`() {
        assertTrue(shouldFallbackToFree(LlmException.Auth(401, "unauthorized")))
        assertTrue(shouldFallbackToFree(LlmException.Auth(403, "forbidden")))
    }

    @Test
    fun `timeout falls back to free`() {
        assertTrue(shouldFallbackToFree(LlmException.Timeout("timed out")))
    }

    @Test
    fun `provider error with the known relayed-failure phrase falls back to free`() {
        assertTrue(shouldFallbackToFree(LlmException.Provider(null, "provider returned error")))
    }

    @Test
    fun `a plain provider error (our own bad request) does not trigger fallback`() {
        // Regression guard: a plain 400/500 must not automatically fall back — that would
        // mask our own malformed-request bugs behind a "the free model handled it" success.
        assertFalse(shouldFallbackToFree(LlmException.Provider(400, "OpenRouter HTTP 400: bad request")))
        assertFalse(shouldFallbackToFree(LlmException.Provider(500, "OpenRouter HTTP 500: server error")))
    }

    @Test
    fun `an unrelated exception does not trigger fallback`() {
        assertFalse(shouldFallbackToFree(IllegalStateException("schema validation failed")))
    }

    // ── isExpectedLlmFailure: what runWithFallback/chat/bootstrap treat as "safe to degrade
    //    to a canned plan" vs "let it surface as a 500" (Wave 1 task 1.4) ──────────────────

    @Test
    fun `every LlmException subtype is an expected failure`() {
        assertTrue(isExpectedLlmFailure(LlmException.RateLimited("429")))
        assertTrue(isExpectedLlmFailure(LlmException.PaymentRequired("402")))
        assertTrue(isExpectedLlmFailure(LlmException.Auth(401, "unauthorized")))
        assertTrue(isExpectedLlmFailure(LlmException.Timeout("timed out")))
        assertTrue(isExpectedLlmFailure(LlmException.Provider(500, "server error")))
    }

    @Test
    fun `pipeline retry-budget exhaustion is an expected failure`() {
        assertTrue(isExpectedLlmFailure(LlmPipelineExhaustedException("LLM training failed after 3 attempts")))
    }

    @Test
    fun `our own withTimeout expiring is an expected failure`() = runBlocking {
        val caught = runCatching { withTimeout(1) { delay(200); "unreachable" } }.exceptionOrNull()
        assertTrue(caught is TimeoutCancellationException, "expected a real TimeoutCancellationException, got $caught")
        assertTrue(isExpectedLlmFailure(caught!!))
    }

    @Test
    fun `a plain bug is NOT an expected failure and must surface, not silently degrade`() {
        assertFalse(isExpectedLlmFailure(NullPointerException("oops")))
        assertFalse(isExpectedLlmFailure(IllegalStateException("trainingPlan missing in bundle")))
    }
}

package com.vtempe.server

import com.vtempe.server.features.ai.data.llm.LlmException
import com.vtempe.server.features.ai.data.service.shouldFallbackToFree
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
}

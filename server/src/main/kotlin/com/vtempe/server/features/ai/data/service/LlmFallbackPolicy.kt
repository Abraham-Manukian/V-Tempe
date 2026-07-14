package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.llm.LlmException

/**
 * Shared free/paid fallback decision — was duplicated verbatim in AiService and ChatService
 * (see ARCHITECTURE_SECURITY_BACKLOG.md A5/N1). Branches on the typed [LlmException]
 * hierarchy first; the string checks are a legacy fallback for anything not yet mapped to a
 * typed exception at the OpenRouterLLMClient boundary.
 */
internal fun shouldFallbackToFree(error: Throwable): Boolean = when (error) {
    is LlmException.RateLimited, is LlmException.PaymentRequired,
    is LlmException.Auth, is LlmException.Timeout -> true
    else -> {
        // Legacy string fallback — deliberately covers LlmException.Provider too, not just
        // untyped exceptions: the original behavior only fell back on a specific relayed
        // phrase ("provider returned error"), not on every 4xx/5xx. Bucketing all Provider
        // errors as an automatic fallback would mask our own malformed-request bugs (a plain
        // 400) behind a "the free model handled it" success. Remove this branch only once
        // Provider is split into narrower typed cases that make that call explicitly.
        val message = error.message?.lowercase().orEmpty()
        message.contains(" 429") || message.contains("rate limit") ||
            message.contains(" 402") || message.contains("insufficient credits") || message.contains("payment required") ||
            message.contains(" 401") || message.contains("unauthorized") ||
            message.contains(" 403") || message.contains("forbidden") ||
            message.contains("timed out") || message.contains("timeout") ||
            message.contains("connection reset") || message.contains("provider returned error")
    }
}

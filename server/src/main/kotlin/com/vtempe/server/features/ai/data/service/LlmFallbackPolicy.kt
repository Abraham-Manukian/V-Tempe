package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.llm.LlmException
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipelineExhaustedException
import kotlinx.coroutines.TimeoutCancellationException

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

/**
 * True for failure modes the LLM pipeline is DESIGNED to hit and recover from (upstream API
 * errors, our own request timing out, the model exhausting its JSON-repair retry budget).
 * False for anything else — a plain bug (NPE, unexpected SerializationException, etc.) — which
 * top-level callers should let propagate into a 500 instead of silently returning a canned
 * fallback plan. See ARCHITECTURE_SECURITY_BACKLOG.md N2: swallowing every Throwable here is
 * exactly what let this session's real bugs (fallback-nutrition not scaling, deload not
 * deduping, etc.) hide behind "the LLM must have been slow" for who knows how long.
 *
 * Deliberately scoped to the TOP-LEVEL entry points (AiService.runWithFallback,
 * ChatService.chat/bootstrap) — the nested per-section fallback inside
 * AiService.attemptDecomposedBundle is a different, intentional multi-strategy resilience
 * design (degrade one section gracefully while the others still succeed) and is left as-is.
 */
internal fun isExpectedLlmFailure(error: Throwable): Boolean = when (error) {
    is LlmException -> true
    is LlmPipelineExhaustedException -> true
    is TimeoutCancellationException -> true
    else -> false
}

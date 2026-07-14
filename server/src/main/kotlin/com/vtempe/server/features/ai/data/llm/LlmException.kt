package com.vtempe.server.features.ai.data.llm

/**
 * Typed LLM/OpenRouter failures, mapped from HTTP status at the OpenRouterLLMClient boundary
 * (see [OpenRouterLLMClient] `mapResponseException`/`requestCompletion`). Callers branch on
 * type via `is`, not by parsing `error.message` text for substrings like " 429" or "timeout" —
 * see ARCHITECTURE_SECURITY_BACKLOG.md items N2/N3.
 *
 * Replaces the old standalone `RateLimitException` — [RateLimited] carries the same
 * `retryAfterMillis` hint.
 */
sealed class LlmException(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** HTTP 429. [retryAfterMillis] is the upstream Retry-After hint, in ms, if the response sent one. */
    class RateLimited(message: String, val retryAfterMillis: Long? = null, cause: Throwable? = null) :
        LlmException(message, cause)

    /** HTTP 402 — insufficient credits / payment required on the OpenRouter account. */
    class PaymentRequired(message: String, cause: Throwable? = null) : LlmException(message, cause)

    /** HTTP 401/403 — missing, invalid, or unauthorized API key. */
    class Auth(val status: Int, message: String, cause: Throwable? = null) : LlmException(message, cause)

    /** Request or socket timeout talking to OpenRouter (no HTTP status — the call never completed). */
    class Timeout(message: String, cause: Throwable? = null) : LlmException(message, cause)

    /**
     * Any other non-2xx OpenRouter/provider response (5xx, unmapped 4xx, or a provider-relayed
     * error surfaced in the response body's `error` field, e.g. "model not found").
     * [status] is null when the provider reported an error without a parseable HTTP-like code.
     */
    class Provider(val status: Int?, message: String, cause: Throwable? = null) : LlmException(message, cause)
}

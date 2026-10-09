package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.access.LlmTier
import com.vtempe.server.features.ai.data.llm.LLMClient
import com.vtempe.server.shared.dto.profile.AiProfile
import org.slf4j.Logger

/**
 * Routes a generation request to the paid LLM client, falling back to the free client on an
 * expected failure (see [shouldFallbackToFree] in LlmFallbackPolicy.kt). The paid client is used
 * only for `llmMode = "paid"`, which the AI routes set solely when the server granted that tier
 * (see AiAccessResolver); "free", null and unknown values all go straight to free.
 *
 * Extracted out of AiService (item A5, God object) — this was ALSO byte-identical duplicated
 * logic in ChatService (a second instance of the same A5/N1-style copy-paste this backlog
 * already flagged for shouldFallbackToFree). Both services now share this one implementation.
 * Each service constructs its own instance since they use different paid/free client pairs
 * (AiService's paid client is the "llm-bootstrap" role — Claude Sonnet; ChatService's paid
 * client is "llm-paid" — Gemini Flash) — not a Koin singleton, plain construction, no DI change.
 */
class LlmClientRouter(
    private val paidLlmClient: LLMClient,
    private val freeLlmClient: LLMClient,
) {
    suspend fun generateWithFallback(
        logger: Logger,
        profile: AiProfile,
        prompt: String,
        operation: String,
        requestId: String,
        /** Response schema for constrained decoding; ignored unless AI_STRUCTURED_OUTPUT is on. */
        schema: com.vtempe.server.features.ai.data.llm.schema.ResponseSchema? = null,
    ): String {
        val mode = profile.llmMode?.trim()?.lowercase()
        if (mode != LlmTier.PAID.wireValue) return freeLlmClient.generateJson(prompt, schema)

        return runCatching {
            paidLlmClient.generateJson(prompt, schema)
        }.recoverCatching { ex ->
            if (!shouldFallbackToFree(ex)) throw ex
            logger.warn(
                "LLM {} switching to free client requestId={} reason={}",
                operation,
                requestId,
                ex.message ?: ex::class.simpleName
            )
            freeLlmClient.generateJson(prompt, schema)
        }.getOrThrow()
    }
}

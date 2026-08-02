package com.vtempe.server.features.ai.data.llm

import com.vtempe.server.config.Env
import java.util.Locale

/**
 * Kill-switch for JSON-Schema-constrained decoding (`response_format: json_schema`).
 *
 * Defaults to **off**: turning it on changes how every AI request is routed (OpenRouter will only
 * pick endpoints that support the parameter), so it must be an explicit, reversible decision made
 * against a deployment that can be watched — not something a code deploy silently switches on for
 * everyone. Flip `AI_STRUCTURED_OUTPUT=true` to enable.
 *
 * Even when enabled, OpenRouterLLMClient degrades to an unconstrained call if a provider rejects
 * the schema, so the worst case is "no guarantee", never "generation fails".
 */
internal object StructuredOutputPolicy {
    val isEnabled: Boolean = Env["AI_STRUCTURED_OUTPUT"]
        ?.trim()
        ?.lowercase(Locale.US)
        ?.let { it == "true" || it == "1" || it == "on" || it == "yes" }
        ?: false
}

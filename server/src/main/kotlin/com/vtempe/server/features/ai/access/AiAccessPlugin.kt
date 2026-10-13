package com.vtempe.server.features.ai.access

import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.http.StopWhenHandledHook
import io.ktor.http.HttpHeaders
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond
import io.ktor.util.AttributeKey

/** Set by [AiAccessPlugin] for every admitted `/ai` call. */
val AiAccessKey: AttributeKey<AiAccessDecision.Granted> = AttributeKey("aiAccess")

class AiAccessPluginConfig {
    lateinit var resolver: AiAccessResolver
}

/**
 * Installed on the `route("/ai")` subtree itself, so it runs for every call the router resolved
 * to an AI handler — including request targets like `/%61i/training` that only become `/ai/…`
 * after Ktor decodes path segments (VT-AUD-002). A raw-URI prefix check in an application-wide
 * interceptor cannot see those. Runs before the handler reads the body.
 */
val AiAccessPlugin = createRouteScopedPlugin("AiAccess", ::AiAccessPluginConfig) {
    val resolver = pluginConfig.resolver
    on(StopWhenHandledHook) { call ->
        val headers = call.request.headers
        when (val decision = resolver.resolve(headers["X-App-Token"], headers[HttpHeaders.Authorization])) {
            is AiAccessDecision.Granted -> call.attributes.put(AiAccessKey, decision)
            is AiAccessDecision.Denied -> call.respond(decision.status, mapOf("error" to decision.error))
        }
    }
}

/** The client profile with `llmMode` replaced by the server-resolved one for this call. */
fun ApplicationCall.authorizedProfile(profile: AiProfile): AiProfile {
    val access = attributes[AiAccessKey]
    return profile.copy(llmMode = resolveLlmMode(profile.llmMode, access.maxTier))
}

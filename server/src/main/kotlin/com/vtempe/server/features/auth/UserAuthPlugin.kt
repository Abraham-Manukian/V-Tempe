package com.vtempe.server.features.auth

import com.vtempe.server.shared.http.StopWhenHandledHook
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.response.respond

class UserAuthPluginConfig {
    lateinit var verifier: IdTokenVerifier
}

/**
 * Per-user auth for the `/me` subtree: a Firebase ID token in `Authorization: Bearer`, verified
 * by [IdTokenVerifier]; the caller's uid goes to [UserIdKey]. Installed on the route itself, like
 * the AI access plugin, so it covers every call the router resolved to a `/me` handler —
 * including targets like `/%6De/sync` that only become `/me/…` after Ktor decodes path segments,
 * which a path-prefix check in an application-wide interceptor never sees.
 */
val UserAuthPlugin = createRouteScopedPlugin("UserAuth", ::UserAuthPluginConfig) {
    val verifier = pluginConfig.verifier
    on(StopWhenHandledHook) { call ->
        val header = call.request.headers[HttpHeaders.Authorization]
        val idToken = header?.removePrefix("Bearer ")?.takeIf { it != header && it.isNotBlank() }
        val userId = idToken?.let { verifier.verify(it) }
        if (userId == null) {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
        } else {
            call.attributes.put(UserIdKey, userId)
        }
    }
}

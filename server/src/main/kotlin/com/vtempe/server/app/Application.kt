package com.vtempe.server.app

import com.vtempe.server.app.di.serverModule
import com.vtempe.server.config.Env
import com.vtempe.server.features.account.api.registerAccountRoutes
import com.vtempe.server.features.ai.access.AiAccessConfig
import com.vtempe.server.features.ai.api.registerAiRoutes
import com.vtempe.server.features.auth.UserIdKey
import com.vtempe.server.features.auth.data.FirebaseTokenVerifier
import com.vtempe.server.features.entitlement.api.registerEntitlementRoutes
import com.vtempe.server.features.payments.yookassa.api.registerYooKassaWebhookRoutes
import com.vtempe.server.features.sync.api.registerSyncRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.path
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlin.time.Duration.Companion.minutes

fun main() {
    val port = Env["PORT"]?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port) {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(Koin) {
        slf4jLogger()
        modules(serverModule)
    }

    // Native mobile clients don't send an Origin header, so there is nothing for CORS to
    // allowlist — no install(CORS) needed here at all.

    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

    // Cloud Run terminates TLS and proxies requests; without this, ApplicationRequest.origin
    // (and therefore the rate-limit key below) resolves to the proxy's address for every
    // caller, collapsing the per-caller limit into one shared bucket.
    // useLastProxy(): Cloud Run appends the real client IP as the LAST entry in
    // X-Forwarded-For; the default (first entry) is attacker-controlled and would let a
    // caller spoof a fresh rate-limit bucket on every request.
    install(XForwardedHeaders) { useLastProxy() }

    // Rate limiting: max 30 AI requests per minute per IP
    install(RateLimit) {
        register(RateLimitName("ai")) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }
    }

    // /ai/* access (app token, Firebase session, paid tier) is enforced by AiAccessPlugin on the
    // AI route subtree itself — see registerAiRoutes. Only the configuration is reported here.
    val aiAccess: AiAccessConfig by inject()
    when {
        aiAccess.appSecret != null -> Unit
        aiAccess.allowMissingAppSecret ->
            log.warn("APP_SECRET is not set and ${AiAccessConfig.ALLOW_MISSING_APP_SECRET}=true — /ai/* skips the app token check (dev only)")
        else -> log.error("APP_SECRET is not set — /ai/* endpoints answer 503 until it is configured")
    }
    log.info("AI access: paid tier policy={}, guests={}", aiAccess.paidAccessPolicy, aiAccess.guestAccessEnabled)

    // Per-user auth for /me/* routes (entitlement, and future per-user data) — a Firebase ID
    // token in the Authorization header, verified against the project's public keys (see
    // FirebaseTokenVerifier). APP_SECRET proves "this is our app build", the Firebase token
    // proves "this is a specific person".
    val firebaseTokenVerifier: FirebaseTokenVerifier by inject()
    intercept(ApplicationCallPipeline.Plugins) {
        // path(), not uri: "/me" itself (DELETE /me, account deletion) must match too, and a
        // query string must not let "/me?..." slip past the check.
        val path = call.request.path()
        if (path == "/me" || path.startsWith("/me/")) {
            val authHeader = call.request.headers["Authorization"]
            val idToken = authHeader?.removePrefix("Bearer ")?.takeIf { it != authHeader }
            val userId = idToken?.let { firebaseTokenVerifier.verify(it) }
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                finish()
            } else {
                call.attributes.put(UserIdKey, userId)
            }
        }
    }

    routing {
        rateLimit(RateLimitName("ai")) {
            registerAiRoutes()
        }
        registerEntitlementRoutes()
        registerSyncRoutes()
        registerAccountRoutes()
        registerYooKassaWebhookRoutes()
        get("/health") { call.respondText("OK") }
    }
}









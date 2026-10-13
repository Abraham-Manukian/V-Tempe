package com.vtempe.server.app

import com.vtempe.server.app.di.serverModule
import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.access.AiAccessConfig
import com.vtempe.server.features.ai.api.registerAiRoutes
import com.vtempe.server.features.auth.IdTokenVerifier
import com.vtempe.server.features.entitlement.data.db.DatabaseConnector
import com.vtempe.server.features.payments.yookassa.api.registerYooKassaWebhookRoutes
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    installStorageUnavailableResponse()

    // Connect to the configured database right away (off the startup thread) so a failure is
    // logged at boot; requests retry it until it is ready (VT-AUD-079).
    val database: DatabaseConnector by inject()
    launch(Dispatchers.IO) { database.state() }

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

    // Per-user auth for /me and everything under it: a Firebase ID token, verified against the
    // project's public keys (see FirebaseTokenVerifier), enforced by UserAuthPlugin on the /me
    // route subtree (registerUserRoutes). APP_SECRET proves "this is our app build", the Firebase
    // token proves "this is a specific person".
    val idTokenVerifier: IdTokenVerifier by inject()

    routing {
        rateLimit(RateLimitName("ai")) {
            registerAiRoutes()
        }
        registerUserRoutes(idTokenVerifier)
        registerYooKassaWebhookRoutes()
        get("/health") { call.respondText("OK") }
        readinessRoute(database)
    }
}









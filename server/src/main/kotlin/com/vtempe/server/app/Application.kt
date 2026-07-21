package com.vtempe.server.app

import com.vtempe.server.app.di.serverModule
import com.vtempe.server.config.Env
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
import io.ktor.server.request.uri
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.security.MessageDigest
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

    // Token-based protection for /ai/* routes.
    // Set APP_SECRET in .env.local; if absent, routes remain open (local dev fallback).
    val appSecret = Env["APP_SECRET"]?.takeIf { it.isNotBlank() }
    if (appSecret == null) {
        log.warn("APP_SECRET is not set — /ai/* endpoints are unprotected!")
    }
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.uri
        if (path.startsWith("/ai/") && appSecret != null) {
            val token = call.request.headers["X-App-Token"]
            val tokenBytes = token?.toByteArray()
            val secretBytes = appSecret.toByteArray()
            val matches = tokenBytes != null && MessageDigest.isEqual(tokenBytes, secretBytes)
            if (!matches) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                finish()
            }
        }
    }

    // Per-user auth for /me/* routes (entitlement, and future per-user data) — a Firebase ID
    // token in the Authorization header, verified against the project's public keys (see
    // FirebaseTokenVerifier). This is independent of the /ai/* APP_SECRET check above: APP_SECRET
    // proves "this is our app build", the Firebase token proves "this is a specific person".
    val firebaseTokenVerifier: FirebaseTokenVerifier by inject()
    intercept(ApplicationCallPipeline.Plugins) {
        val path = call.request.uri
        if (path.startsWith("/me/")) {
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
        registerYooKassaWebhookRoutes()
        get("/health") { call.respondText("OK") }
    }
}









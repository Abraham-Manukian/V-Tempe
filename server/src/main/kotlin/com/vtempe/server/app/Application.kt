package com.vtempe.server.app


import com.vtempe.server.app.di.serverModule
import com.vtempe.server.config.Env
import com.vtempe.server.features.ai.api.registerAiRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.uri
import kotlinx.serialization.json.Json
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

    install(CORS) { anyHost() }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
    }

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
            if (token != appSecret) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Unauthorized"))
                finish()
            }
        }
    }

    routing {
        rateLimit(RateLimitName("ai")) {
            registerAiRoutes()
        }
        get("/health") { call.respondText("OK") }
    }
}









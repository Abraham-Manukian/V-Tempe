package com.vtempe.shared.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Exercises the same BearerAuth client-plugin logic createHttpClient() installs (see Api.kt),
 * against a MockEngine so no real network call happens. Duplicated here as a standalone plugin
 * build rather than calling createHttpClient() directly, since that function hardcodes the
 * OkHttp/Darwin engine indirectly via no engine param — MockEngine needs to be the engine itself.
 */
private fun mockClientWithBearerAuth(
    provider: (suspend () -> String?)?,
    handler: MockEngine
) = HttpClient(handler) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    install(HttpTimeout)
    install(Logging) { level = LogLevel.NONE }
    if (provider != null) {
        install(createClientPlugin("BearerAuth") {
            onRequest { request, _ ->
                val path = request.url.encodedPath
                if ((path.startsWith("/me/") || path.startsWith("/ai/")) &&
                    request.headers[HttpHeaders.Authorization] == null
                ) {
                    val token = runCatching { provider() }.getOrNull()
                    if (token != null) {
                        request.headers.append(HttpHeaders.Authorization, "Bearer $token")
                    }
                }
            }
        })
    }
}

class ApiBearerAuthTest {

    private fun okEngine(captured: MutableList<String?>) = MockEngine { request ->
        captured += request.headers[HttpHeaders.Authorization]
        respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @Test
    fun `attaches bearer token on a me path when provider returns a token`() = runBlocking {
        val captured = mutableListOf<String?>()
        val client = mockClientWithBearerAuth({ "test-token" }, okEngine(captured))
        client.get("https://example.test/me/entitlement")
        assertEquals("Bearer test-token", captured.single())
    }

    @Test
    fun `attaches bearer token on an ai path too`() = runBlocking {
        val captured = mutableListOf<String?>()
        val client = mockClientWithBearerAuth({ "test-token" }, okEngine(captured))
        client.get("https://example.test/ai/training")
        assertEquals("Bearer test-token", captured.single())
    }

    @Test
    fun `no header when provider returns null`() = runBlocking {
        val captured = mutableListOf<String?>()
        val client = mockClientWithBearerAuth({ null }, okEngine(captured))
        client.get("https://example.test/me/entitlement")
        assertNull(captured.single())
    }

    @Test
    fun `no header for a path outside me and ai`() = runBlocking {
        val captured = mutableListOf<String?>()
        val client = mockClientWithBearerAuth({ "test-token" }, okEngine(captured))
        client.get("https://example.test/health")
        assertNull(captured.single())
    }

    @Test
    fun `request still succeeds when the provider throws`() = runBlocking {
        val captured = mutableListOf<String?>()
        val client = mockClientWithBearerAuth({ error("boom") }, okEngine(captured))
        client.get("https://example.test/me/entitlement")
        assertNull(captured.single())
    }
}

package com.vtempe.server

import com.vtempe.server.features.ai.api.AiInputRejected
import com.vtempe.server.features.ai.api.receiveBounded
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Runs requests through the real Netty engine, which every other server test bypasses
 * (`testApplication` uses Ktor's test engine). It pins the pairing of ktor-server-netty with the
 * Netty version forced by netty-bom: startup, HTTP/1.1 parsing of Content-Length and chunked
 * bodies, and the forwarded-header handling the rate-limit key relies on.
 */
class NettyEngineSmokeTest {

    @Serializable
    private data class Probe(val text: String)

    private lateinit var server: ApplicationEngine
    private lateinit var baseUrl: String
    private val client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()

    @BeforeEach
    fun start() {
        server = embeddedServer(Netty, port = 0) {
            install(XForwardedHeaders) { useLastProxy() }
            routing {
                get("/health") { call.respondText("OK") }
                get("/caller") { call.respondText(call.request.origin.remoteHost) }
                post("/bounded") {
                    try {
                        call.respondText(call.receiveBounded<Probe>(maxBytes = LIMIT).text)
                    } catch (e: AiInputRejected) {
                        call.respondText(e.code, status = e.status)
                    }
                }
                post("/echo-length") { call.respondText(call.receiveText().length.toString()) }
            }
        }.start(wait = false)
        val port = runBlocking { server.resolvedConnectors().first().port }
        baseUrl = "http://127.0.0.1:$port"
    }

    @AfterEach
    fun stop() {
        server.stop(gracePeriodMillis = 0, timeoutMillis = 2_000)
    }

    @Test
    fun `a GET is served`() {
        val response = send(HttpRequest.newBuilder(uri("/health")).GET())
        assertEquals(200, response.statusCode())
        assertEquals("OK", response.body())
    }

    @Test
    fun `a body with Content-Length is decoded`() {
        val response = send(post("/bounded", HttpRequest.BodyPublishers.ofString("""{"text":"hello"}""")))
        assertEquals(200, response.statusCode())
        assertEquals("hello", response.body())
    }

    @Test
    fun `a chunked body is decoded`() {
        val response = send(post("/bounded", chunked("""{"text":"chunked"}""".toByteArray())))
        assertEquals(200, response.statusCode())
        assertEquals("chunked", response.body())
    }

    @Test
    fun `a chunked body over the limit is refused with 413`() {
        val oversized = """{"text":"${"x".repeat(LIMIT)}"}""".toByteArray()
        val response = send(post("/bounded", chunked(oversized)))
        assertEquals(413, response.statusCode())
        assertEquals("request_too_large", response.body())
    }

    @Test
    fun `a declared Content-Length over the limit is refused with 413`() {
        val oversized = """{"text":"${"x".repeat(LIMIT)}"}"""
        val response = send(post("/bounded", HttpRequest.BodyPublishers.ofString(oversized)))
        assertEquals(413, response.statusCode())
    }

    @Test
    fun `a large chunked body arrives intact`() {
        val size = 256 * 1024
        val response = send(post("/echo-length", chunked(ByteArray(size) { 'a'.code.toByte() })))
        assertEquals(200, response.statusCode())
        assertEquals(size.toString(), response.body())
    }

    @Test
    fun `the caller address is the last X-Forwarded-For entry`() {
        val response = send(HttpRequest.newBuilder(uri("/caller")).header("X-Forwarded-For", "1.1.1.1, 9.9.9.9").GET())
        assertEquals("9.9.9.9", response.body())
    }

    @Test
    fun `one connection serves consecutive requests`() {
        repeat(3) { assertEquals(200, send(HttpRequest.newBuilder(uri("/health")).GET()).statusCode()) }
    }

    private fun uri(path: String) = URI.create(baseUrl + path)

    private fun post(path: String, body: HttpRequest.BodyPublisher) =
        HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json").POST(body)

    /** A publisher of unknown length, which the JDK client sends with Transfer-Encoding: chunked. */
    private fun chunked(bytes: ByteArray) = HttpRequest.BodyPublishers.ofInputStream { ByteArrayInputStream(bytes) }

    private fun send(request: HttpRequest.Builder): HttpResponse<String> =
        client.send(request.build(), HttpResponse.BodyHandlers.ofString())

    private companion object {
        const val LIMIT = 1024
    }
}

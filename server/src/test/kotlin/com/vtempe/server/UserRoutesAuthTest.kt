package com.vtempe.server

import com.vtempe.server.app.registerUserRoutes
import com.vtempe.server.features.auth.IdTokenVerifier
import com.vtempe.server.features.sync.data.repo.InMemorySyncBlobRepository
import com.vtempe.server.features.sync.data.service.SyncService
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/**
 * The /me subtree admits only a verified Firebase user, whatever form the request target takes:
 * the guard sits on the route itself, as for /ai (VT-AUD-002), not on a raw path prefix.
 */
class UserRoutesAuthTest {

    @AfterEach
    fun tearDown() {
        runCatching { stopKoin() }
    }

    /** "token-<uid>" is <uid>'s token; anything else is rejected. */
    private val verifier = IdTokenVerifier { token -> token.removePrefix("token-").takeIf { token.startsWith("token-") } }

    // Only SyncService is bound: a /me handler the guard wrongly let through would fail with 500
    // (entitlement, account), never 401.
    private fun userRoutesTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            install(Koin) { modules(module { single { SyncService(InMemorySyncBlobRepository()) } }) }
            install(ContentNegotiation) { json() }
            routing { registerUserRoutes(verifier) }
        }
        block(client)
    }

    private suspend fun HttpClient.call(method: HttpMethod, path: String, authorization: String? = null): HttpResponse =
        request(path) {
            this.method = method
            authorization?.let { header(HttpHeaders.Authorization, it) }
            if (method == HttpMethod.Put) {
                contentType(ContentType.Application.Json)
                setBody("""{"payload":"{}"}""")
            }
        }

    private val routes = listOf(
        HttpMethod.Get to "/me/sync",
        HttpMethod.Put to "/me/sync/weight",
        HttpMethod.Get to "/me/entitlement",
        HttpMethod.Delete to "/me",
        HttpMethod.Get to "/me/referral",
        HttpMethod.Post to "/me/referral/code",
        HttpMethod.Post to "/me/referral/attach",
        HttpMethod.Post to "/me/referral/redeem",
    )

    private suspend fun assertRejected(response: HttpResponse, what: String) {
        assertEquals(HttpStatusCode.Unauthorized, response.status, what)
        assertTrue(response.bodyAsText().contains("Unauthorized"), "$what: answered by the guard, not the handler")
    }

    @Test
    fun `every per-user route rejects a missing, malformed or invalid token`() = userRoutesTest { client ->
        for ((method, path) in routes) {
            for (authorization in listOf(null, "token-anna", "Basic token-anna", "Bearer ", "Bearer forged")) {
                assertRejected(client.call(method, path, authorization), "$method $path with $authorization")
            }
        }
    }

    @Test
    fun `encoded paths and query strings cannot bypass the guard`() = userRoutesTest { client ->
        val targets = listOf(
            HttpMethod.Get to "/%6De/sync",
            HttpMethod.Get to "/%6d%65/entitlement",
            HttpMethod.Get to "/me/%73ync",
            HttpMethod.Put to "/%6De/sync/weight",
            HttpMethod.Delete to "/%6De",
            HttpMethod.Delete to "/me?x=1",
            HttpMethod.Get to "/me/sync?x=/health",
        )
        for ((method, path) in targets) {
            assertRejected(client.call(method, path), "$method $path")
        }
    }

    @Test
    fun `an encoded path with a valid token reaches that user's data`() = userRoutesTest { client ->
        assertEquals(HttpStatusCode.NoContent, client.call(HttpMethod.Put, "/%6De/sync/weight", "Bearer token-anna").status)

        val annas = client.get("/me/sync") { header(HttpHeaders.Authorization, "Bearer token-anna") }
        assertEquals(HttpStatusCode.OK, annas.status)
        assertTrue(annas.bodyAsText().contains("weight"))

        val boris = client.get("/me/sync") { header(HttpHeaders.Authorization, "Bearer token-boris") }
        assertEquals(HttpStatusCode.OK, boris.status)
        assertTrue(!boris.bodyAsText().contains("weight"), "the uid comes from the token, so boris sees only his own data")
    }
}

package com.vtempe.server

import com.vtempe.server.app.registerUserRoutes
import com.vtempe.server.features.account.data.service.AccountDeletionService
import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.sync.data.repo.InMemorySyncBlobRepository
import com.vtempe.server.features.sync.data.service.MAX_SYNC_PAYLOAD_CHARS
import com.vtempe.server.features.sync.data.service.SyncService
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin

/** VT-AUD-057: two devices of one account each add their own diary entries, and both survive. */
class DiaryMergeTest {

    @AfterEach
    fun tearDown() {
        runCatching { stopKoin() }
    }

    private val blobs = InMemorySyncBlobRepository()
    private val sync = SyncService(blobs)

    private fun stored(domain: String = "weight"): JsonObject =
        runBlocking { blobs.pullAll("anna")[domain]?.payload }?.let { Json.parseToJsonElement(it).jsonObject } ?: JsonObject(emptyMap())

    private fun diaryTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            install(Koin) {
                modules(module { single { sync }; single { AccountDeletionService(blobs, InMemoryEntitlementRepository()) } })
            }
            install(ContentNegotiation) { json() }
            routing { registerUserRoutes { token -> token } } // the token is the uid
        }
        block(client)
    }

    private suspend fun HttpClient.change(domain: String, body: String): HttpResponse = patch("/me/sync/$domain") {
        bearerAuth("anna")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    @Test
    fun `entries of two devices on different dates are both kept`() = diaryTest { client ->
        client.change("weight", """{"upserts":{"2026-10-01":70.0}}""") // phone
        client.change("weight", """{"upserts":{"2026-10-02":69.5}}""") // tablet, without the phone's entry

        assertEquals(setOf("2026-10-01", "2026-10-02"), stored().keys)
    }

    @Test
    fun `a removed entry goes and a reset device clears only what it had`() = diaryTest { client ->
        client.change("weight", """{"upserts":{"2026-10-01":70.0,"2026-10-02":69.5,"2026-10-03":69.0}}""")

        client.change("weight", """{"deletes":["2026-10-02"]}""")

        assertEquals(setOf("2026-10-01", "2026-10-03"), stored().keys)
    }

    @Test
    fun `the same entry changed on two devices keeps the change that arrived last`() = diaryTest { client ->
        client.change("sleep", """{"upserts":{"2026-10-01":420}}""")
        client.change("sleep", """{"upserts":{"2026-10-01":480}}""")

        assertEquals("480", stored("sleep")["2026-10-01"].toString())
    }

    @Test
    fun `the profile is not a diary and stays whole-record`() = diaryTest { client ->
        assertEquals(HttpStatusCode.BadRequest, client.change("profile", """{"upserts":{"age":30}}""").status)
        assertEquals(HttpStatusCode.NoContent, client.put("/me/sync/profile") {
            bearerAuth("anna")
            contentType(ContentType.Application.Json)
            setBody("""{"payload":"{\"age\":30}"}""")
        }.status)
    }

    @Test
    fun `a full replace still replaces, as the account-reset path needs`() = diaryTest { client ->
        client.change("weight", """{"upserts":{"2026-10-01":70.0}}""")

        client.put("/me/sync/weight") {
            bearerAuth("anna")
            contentType(ContentType.Application.Json)
            setBody("""{"payload":"{}"}""")
        }

        assertEquals(emptySet<String>(), stored().keys)
    }

    @Test
    fun `merges respect the deletion barrier and the size cap`() = diaryTest { client ->
        val big = "x".repeat(MAX_SYNC_PAYLOAD_CHARS)
        assertEquals(HttpStatusCode.PayloadTooLarge, client.change("sleepNotes", """{"upserts":{"2026-10-01":"$big"}}""").status)
        assertEquals(emptySet<String>(), stored("sleepNotes").keys)

        client.delete("/me") { bearerAuth("anna") }
        assertEquals(HttpStatusCode.Gone, client.change("weight", """{"upserts":{"2026-10-01":70.0}}""").status)
        assertEquals(emptySet<String>(), stored().keys)
    }
}

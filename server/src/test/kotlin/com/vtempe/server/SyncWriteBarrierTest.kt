package com.vtempe.server

import com.vtempe.server.features.referral.data.repo.InMemoryReferralRepository
import com.vtempe.server.app.registerUserRoutes
import com.vtempe.server.features.account.data.service.AccountDeletionService
import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.sync.data.repo.InMemorySyncBlobRepository
import com.vtempe.server.features.sync.data.service.SyncService
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Duration
import java.time.Instant

/**
 * VT-AUD-059: once an account's data is erased, another device still holding a valid ID token
 * cannot write it back — until every token issued before the deletion has expired.
 */
class SyncWriteBarrierTest {

    @AfterEach
    fun tearDown() {
        runCatching { stopKoin() }
    }

    private var now = Instant.parse("2026-10-13T12:00:00Z")
    private val blobs = InMemorySyncBlobRepository()
    private val sync = SyncService(blobs) { now }
    private val deletion = AccountDeletionService(blobs, InMemoryEntitlementRepository(), InMemoryReferralRepository()) { now }

    @Test
    fun `a push from another device right after deletion is refused and stores nothing`() = runBlocking {
        sync.push("anna", "weight", """{"2026-10-01":70.0}""")

        deletion.deleteAccountData("anna")
        now = now.plus(Duration.ofMinutes(30))

        assertFalse(sync.push("anna", "weight", """{"2026-10-01":70.0}"""))
        assertTrue(blobs.pullAll("anna").isEmpty())
    }

    @Test
    fun `the barrier lasts until every token issued before the deletion has expired`() = runBlocking {
        deletion.deleteAccountData("anna")

        now = now.plus(Duration.ofHours(1)) // the last token issued before deletion expires now
        assertFalse(sync.push("anna", "weight", "{}"), "clock-skew margin still applies")

        now = now.plus(AccountDeletionService.SYNC_WRITE_BARRIER)
        assertTrue(sync.push("anna", "weight", "{}"), "an account whose deletion failed can sync again")
    }

    @Test
    fun `other users keep syncing`() = runBlocking {
        deletion.deleteAccountData("anna")

        assertTrue(sync.push("boris", "weight", "{}"))
        assertEquals("{}", blobs.pullAll("boris")["weight"]?.payload)
    }

    @Test
    fun `a repeated deletion extends the barrier`() = runBlocking {
        deletion.deleteAccountData("anna")
        now = now.plus(Duration.ofMinutes(60))
        deletion.deleteAccountData("anna")
        now = now.plus(Duration.ofMinutes(30))

        assertFalse(sync.push("anna", "weight", "{}"))
    }

    @Test
    fun `the sync route answers 410 and the client keeps its copy for later`() = testApplication {
        application {
            install(Koin) { modules(module { single { sync }; single { deletion } }) }
            install(ContentNegotiation) { json() }
            routing { registerUserRoutes { token -> token } } // the token is the uid
        }

        assertEquals(HttpStatusCode.NoContent, client.delete("/me") { bearerAuth("anna") }.status)
        val write = client.put("/me/sync/weight") {
            bearerAuth("anna")
            contentType(ContentType.Application.Json)
            setBody("""{"payload":"{\"2026-10-01\":70.0}"}""")
        }

        assertEquals(HttpStatusCode.Gone, write.status)
        assertTrue(write.bodyAsText().contains("account_deleted"))
        assertEquals("""{"domains":{}}""", client.get("/me/sync") { bearerAuth("anna") }.bodyAsText())
    }
}

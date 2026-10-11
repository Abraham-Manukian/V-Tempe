package com.vtempe.server

import com.vtempe.server.app.installStorageUnavailableResponse
import com.vtempe.server.app.readinessRoute
import com.vtempe.server.features.auth.UserIdKey
import com.vtempe.server.features.entitlement.data.db.DatabaseConnector
import com.vtempe.server.features.entitlement.data.db.DatabaseState
import com.vtempe.server.features.entitlement.data.db.StorageUnavailableException
import com.vtempe.server.features.sync.api.registerSyncRoutes
import com.vtempe.server.features.sync.data.repo.ExposedSyncBlobRepository
import com.vtempe.server.features.sync.data.service.SyncService
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** VT-AUD-079: a configured database that fails is never replaced by memory. */
class DatabaseAvailabilityTest {

    @AfterEach
    fun tearDown() {
        runCatching { stopKoin() }
    }

    // A placeholder Database: these tests never run a query against it.
    private val fakeDatabase = Database.connect("jdbc:postgresql://localhost:1/none", driver = "org.postgresql.Driver")

    @Test
    fun `a failed first connection is retried later and then shared`() {
        var now = 0L
        val opens = AtomicInteger()
        var failing = true
        val connector = DatabaseConnector("jdbc:postgresql://db/app", open = {
            opens.incrementAndGet()
            if (failing) error("connection refused") else fakeDatabase
        }, retryAfterMs = 5_000, nowMs = { now })

        assertEquals(DatabaseState.Unavailable, connector.state())
        assertThrows(StorageUnavailableException::class.java) { connector.require() }
        assertEquals(1, opens.get(), "no retry storm inside the back-off window")

        failing = false
        now = 5_000
        assertSame(fakeDatabase, connector.require(), "the database recovered without a restart")
        connector.state()
        assertEquals(2, opens.get(), "once ready, one shared database")
    }

    @Test
    fun `concurrent first requests open one pool`() {
        val opens = AtomicInteger()
        val connector = DatabaseConnector("jdbc:postgresql://db/app", open = { opens.incrementAndGet(); Thread.sleep(50); fakeDatabase })
        val pool = Executors.newFixedThreadPool(8)
        repeat(8) { pool.submit { connector.require() } }
        pool.shutdown()
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS))
        assertEquals(1, opens.get())
    }

    @Test
    fun `without DATABASE_URL it is local development and never touches a database`() {
        val connector = DatabaseConnector(null, open = { error("must not open") })
        assertEquals(DatabaseState.Disabled, connector.state())
        assertEquals(false, connector.isConfigured)
    }

    @Test
    fun `a sync write while the database is down answers 503, not 204`() = testApplication {
        val connector = DatabaseConnector("jdbc:postgresql://db/app", open = { error("connection refused") })
        application {
            install(Koin) { modules(module { single { SyncService(ExposedSyncBlobRepository(connector::require)) } }) }
            install(ContentNegotiation) { json() }
            installStorageUnavailableResponse()
            intercept(ApplicationCallPipeline.Plugins) { call.attributes.put(UserIdKey, "uid-1") } // the /me auth intercept
            routing {
                registerSyncRoutes()
                readinessRoute(connector)
            }
        }

        val write = client.put("/me/sync/weight") {
            contentType(ContentType.Application.Json)
            setBody("""{"payload":"{}"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, write.status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/me/sync").status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
    }
}

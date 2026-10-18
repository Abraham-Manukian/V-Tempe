package com.vtempe.server

import com.vtempe.server.app.installStorageUnavailableResponse
import com.vtempe.server.app.readinessRoute
import com.vtempe.server.app.registerUserRoutes
import com.vtempe.server.features.entitlement.data.db.DatabaseConnector
import com.vtempe.server.features.entitlement.data.db.DatabaseState
import com.vtempe.server.features.entitlement.data.db.StorageUnavailableException
import com.vtempe.server.features.sync.data.repo.ExposedSyncBlobRepository
import com.vtempe.server.features.sync.data.service.SyncService
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
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
import org.junit.jupiter.api.Timeout
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
    // Separate thread: the old monitor wait is not interruptible, so a same-thread timeout never fires.
    @Timeout(value = 10, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    fun `requests do not queue behind a slow connection attempt`() {
        val attemptStarted = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val connector = DatabaseConnector("jdbc:postgresql://db/app", open = {
            attemptStarted.countDown()
            release.await() // a connect that hangs, like an unreachable Cloud SQL instance
            error("connect timed out")
        }, waitForOpenMs = 100, firstOpenWaitMs = 100)
        val pool = Executors.newSingleThreadExecutor()
        pool.submit { connector.state() }
        assertTrue(attemptStarted.await(5, TimeUnit.SECONDS))

        val startedAt = System.nanoTime()
        val other = connector.state()
        val waitedMs = (System.nanoTime() - startedAt) / 1_000_000

        assertEquals(DatabaseState.Unavailable, other)
        assertTrue(waitedMs < 1_000, "answered after $waitedMs ms instead of waiting for the hung attempt")
        release.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS))
    }

    @Test
    fun `a cold start's first open is waited for, not answered with 503`() {
        val connector = DatabaseConnector("jdbc:postgresql://db/app", open = { Thread.sleep(300); fakeDatabase }, waitForOpenMs = 50)
        val pool = Executors.newFixedThreadPool(4)
        val results = List(4) { pool.submit<DatabaseState> { connector.state() } }

        assertTrue(results.all { it.get(5, TimeUnit.SECONDS) is DatabaseState.Ready }, "every request of the cold start is served")
        pool.shutdown()
    }

    @Test
    fun `an interrupted wait answers Unavailable and keeps the interrupt`() {
        val holding = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val connector = DatabaseConnector("jdbc:postgresql://db/app", open = { holding.countDown(); release.await(); fakeDatabase })
        val pool = Executors.newSingleThreadExecutor()
        try {
            pool.submit { connector.state() }
            assertTrue(holding.await(5, TimeUnit.SECONDS))
            Thread.currentThread().interrupt()

            assertEquals(DatabaseState.Unavailable, connector.state())
            assertTrue(Thread.interrupted(), "the interrupt flag is restored (and cleared here)")
        } finally {
            release.countDown()
            pool.shutdown()
        }
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
            routing {
                registerUserRoutes { "uid-1" } // every token belongs to uid-1
                readinessRoute(connector)
            }
        }

        val write = client.put("/me/sync/weight") {
            bearerAuth("token")
            contentType(ContentType.Application.Json)
            setBody("""{"payload":"{}"}""")
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, write.status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/me/sync") { bearerAuth("token") }.status)
        assertEquals(HttpStatusCode.ServiceUnavailable, client.get("/ready").status)
    }
}

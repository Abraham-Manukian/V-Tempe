package com.vtempe.server

import com.vtempe.server.features.referral.data.repo.ExposedReferralRepository
import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import com.vtempe.server.features.referral.data.service.ReferralRewards
import com.vtempe.server.features.referral.domain.model.DebitResult
import com.vtempe.server.features.referral.domain.model.ReferralPolicy
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import com.vtempe.server.features.referral.domain.model.LedgerKind
import com.vtempe.server.features.account.data.service.AccountDeletionService
import com.vtempe.server.features.entitlement.data.db.DatabaseConnector
import com.vtempe.server.features.entitlement.data.db.Entitlements
import com.vtempe.server.features.entitlement.data.db.Payments
import com.vtempe.server.features.entitlement.data.repo.ExposedEntitlementRepository
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.RefundOutcome
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.sync.data.db.SyncBlobs
import com.vtempe.server.features.sync.data.repo.ExposedSyncBlobRepository
import com.vtempe.server.features.sync.data.service.SyncService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.Duration
import java.time.Instant

/**
 * The durable repositories on a real PostgreSQL (until now they were checked by review and
 * compilation only): the schema upgrade, the per-user locks of payments, refunds, deletion and
 * diary merges under real concurrency.
 *
 * Runs only with VTEMPE_PG_TEST_URL (+ _USER, _PASSWORD) pointing at a throwaway database on
 * localhost - it drops the public schema before every test, so any other host is refused. CI runs
 * it against a PostgreSQL 16 service (quality-gate.yml, which fails if it was skipped); the image
 * build and a plain local run skip it.
 */
class PostgresIntegrationTest {
    private val url: String? = System.getenv("VTEMPE_PG_TEST_URL")
    private lateinit var database: Database
    private val connectors = mutableListOf<Database>()

    private fun open(): Database = DatabaseConnector(url, open = {
        DatabaseConnector.openPostgres(checkNotNull(url), System.getenv("VTEMPE_PG_TEST_USER"), System.getenv("VTEMPE_PG_TEST_PASSWORD"), poolSize = 5)
    }).require().also(connectors::add)

    @BeforeEach
    fun freshDatabase() {
        assumeTrue(url != null, "VTEMPE_PG_TEST_URL not set")
        val host = URI(checkNotNull(url).removePrefix("jdbc:")).host
        check(host == "127.0.0.1" || host == "localhost") { "refusing to wipe a database on $host" }
        val admin = Database.connect(url, user = System.getenv("VTEMPE_PG_TEST_USER"), password = System.getenv("VTEMPE_PG_TEST_PASSWORD"))
        transaction(admin) { exec("DROP SCHEMA public CASCADE"); exec("CREATE SCHEMA public") }
        TransactionManager.closeAndUnregister(admin)
    }

    @AfterEach
    fun close() {
        connectors.forEach(TransactionManager::closeAndUnregister)
    }

    /** Starts [count] calls at once on the IO pool, so they really overlap in the database. */
    private fun <T> concurrently(count: Int, call: suspend (Int) -> T): List<T> = runBlocking {
        val start = CompletableDeferred<Unit>()
        val jobs = (0 until count).map { i -> async(Dispatchers.IO) { start.await(); call(i) } }
        start.complete(Unit)
        jobs.awaitAll()
    }

    // ── Schema ───────────────────────────────────────────────────────────────

    @Test
    fun `a database from before these changes gains the new tables and keeps its data`() {
        val old = Database.connect(checkNotNull(url), user = System.getenv("VTEMPE_PG_TEST_USER"), password = System.getenv("VTEMPE_PG_TEST_PASSWORD"))
        transaction(old) {
            SchemaUtils.create(Entitlements, Payments, SyncBlobs) // what production has today
            Payments.insert {
                it[userId] = "anna"; it[paymentSource] = "YOOKASSA"; it[externalId] = "old-pay"
                it[amountMinor] = 29900; it[currency] = "RUB"; it[rawPayload] = "{}"; it[createdAt] = Instant.EPOCH
            }
            SyncBlobs.insert { it[userId] = "anna"; it[domain] = "weight"; it[payload] = """{"2026-10-01":70.0}"""; it[updatedAt] = Instant.EPOCH }
        }
        TransactionManager.closeAndUnregister(old)

        database = open()
        open() // a restart: creating missing tables again must be a no-op

        val tables = transaction(database) { SchemaUtils.listTables().map { it.substringAfterLast('.') }.toSet() }
        assertTrue(tables.containsAll(setOf("sync_write_barriers", "grant_windows", "payment_refunds")), "tables: $tables")
        transaction(database) {
            assertEquals(1, Payments.selectAll().count())
            assertEquals(1, SyncBlobs.selectAll().count())
        }
    }

    // ── Sync: deletion barrier (VT-AUD-059) and diary merge (VT-AUD-057) ────────

    @Test
    fun `a push racing an account deletion never survives it`() {
        database = open()
        val blobs = ExposedSyncBlobRepository { database }
        val now = Instant.parse("2026-10-13T12:00:00Z")
        val users = (0 until 60).map { "racer-$it" }

        concurrently(users.size * 2) { i ->
            val user = users[i / 2]
            if (i % 2 == 0) blobs.push(user, "weight", """{"2026-10-01":70.0}""", now)
            else blobs.deleteAll(user, now, now.plus(Duration.ofMinutes(65)))
        }

        users.forEach { user ->
            assertTrue(runBlocking { blobs.pullAll(user) }.isEmpty(), "$user: the push landed after the wipe")
            assertEquals(false, runBlocking { blobs.push(user, "weight", "{}", now.plus(Duration.ofMinutes(1))) })
        }
    }

    @Test
    fun `two devices merging at the same time keep every entry`() {
        database = open()
        val sync = SyncService(ExposedSyncBlobRepository { database })

        concurrently(40) { i ->
            sync.merge("anna", "weight", mapOf("2026-09-%02d-device%d".format(i / 2 + 1, i % 2) to JsonPrimitive(70.0 + i)), emptyList())
        }

        val stored = Json.parseToJsonElement(runBlocking { sync.pullAll("anna") }.getValue("weight").payload).jsonObject
        assertEquals(40, stored.size, "a merge read a stale diary and dropped another device's entries")
    }

    @Test
    fun `account deletion wipes data and blocks the next push`() {
        database = open()
        val blobs = ExposedSyncBlobRepository { database }
        val entitlements = ExposedEntitlementRepository { database }
        runBlocking {
            blobs.push("anna", "profile", "{}", Instant.now())
            AccountDeletionService(blobs, entitlements, ExposedReferralRepository { database }).deleteAccountData("anna")
            assertTrue(blobs.pullAll("anna").isEmpty())
            assertEquals(false, blobs.push("anna", "profile", "{}", Instant.now()))
        }
    }

    // ── Payments (VT-AUD-051) and refunds (VT-AUD-052) ───────────────────────

    private val month = Duration.ofDays(30)
    private val start = Instant.parse("2026-01-01T00:00:00Z")

    private fun service(now: () -> Instant = { start }) = EntitlementService(ExposedEntitlementRepository { database }, clock = now)

    private suspend fun EntitlementService.pay(id: String, user: String = "anna") =
        recordPaymentAndExtend(id, user, PaymentSource.YOOKASSA, 29900, "RUB", "{}", month)

    @Test
    fun `parallel payments all extend access`() {
        database = open()
        val service = service()

        concurrently(20) { service.pay("pay-$it") }

        assertEquals(start.plus(month.multipliedBy(20)), runBlocking { service.current("anna").expiresAt })
    }

    @Test
    fun `a payment delivered many times at once is granted once`() {
        database = open()
        val service = service()

        val results = concurrently(8) { runCatching { service.pay("same-pay") } }

        assertTrue(results.all { it.isSuccess }, "a redelivery failed instead of being recognised: $results")
        assertEquals(1, results.count { it.getOrNull() != null }, "results: $results")
        assertEquals(start.plus(month), runBlocking { service.current("anna").expiresAt })
    }

    @Test
    fun `a full refund takes back the unused part and keeps the next month whole`() {
        database = open()
        var now = start
        val service = service { now }
        runBlocking { service.pay("A"); service.pay("B") }
        now = start.plus(Duration.ofDays(19))

        val result = runBlocking { service.applyRefund(VerifiedRefund("r-A", "A", 29900, "RUB", full = true, rawPayload = "{}")) }

        assertEquals(RefundOutcome.ACCESS_REDUCED, result.outcome)
        assertEquals(Duration.ofDays(11), result.removed)
        assertEquals(now.plus(month), runBlocking { service.current("anna").expiresAt })
    }

    @Test
    fun `a refund delivered many times at once applies once`() {
        database = open()
        var now = start
        val service = service { now }
        runBlocking { service.pay("A"); service.pay("B") }
        now = start.plus(Duration.ofDays(10))

        val outcomes = concurrently(6) {
            runCatching { service.applyRefund(VerifiedRefund("r-A", "A", 29900, "RUB", full = true, rawPayload = "{}")).outcome }
        }

        assertTrue(outcomes.all { it.isSuccess }, "a redelivery failed instead of being recognised: $outcomes")
        assertEquals(1, outcomes.count { it.getOrNull() == RefundOutcome.ACCESS_REDUCED }, "outcomes: $outcomes")
        assertEquals(now.plus(month), runBlocking { service.current("anna").expiresAt })
    }

    @Test
    fun `refunds and payments of one user at the same time neither deadlock nor lose time`() {
        database = open()
        var now = start
        val service = service { now }
        runBlocking { (0 until 5).forEach { service.pay("old-$it") } } // Jan 1 – Jun 1
        now = start.plus(Duration.ofDays(10))

        // Refund the running month while five new months are being paid.
        concurrently(6) { i ->
            if (i == 0) service.applyRefund(VerifiedRefund("r-old-0", "old-0", 29900, "RUB", full = true, rawPayload = "{}"))
            else service.pay("new-$i")
        }

        // 10 months bought, the first one refunded on day 10 (its last 20 days removed).
        assertEquals(start.plus(month.multipliedBy(10)).minus(Duration.ofDays(20)), runBlocking { service.current("anna").expiresAt })
    }

    /** boris has a code and [earnedMinor] available (an accrual whose hold ended before [start]). */
    private suspend fun ExposedReferralRepository.borisEarned(earnedMinor: Long) {
        saveCode("boris", "BRSKCDM2", start)
        appendIfAbsent(LedgerEntry("boris", LedgerKind.ACCRUAL, "accrual:anna", "pay-anna", "anna", "RUB", earnedMinor,
            availableAt = start.minus(Duration.ofDays(1)), periodDays = null, completed = false, createdAt = start))
    }

    @Test
    fun `parallel redemptions never spend more than the referral balance`() {
        database = open()
        val referrals = ExposedReferralRepository { database }
        runBlocking { referrals.borisEarned(5980) }

        val results = concurrently(8) { i -> referrals.debit("boris", "redeem:boris:req-$i", "RUB", 2000, 7, start) }

        assertEquals(2, results.count { it is DebitResult.Debited }, "results: $results")
        assertEquals(1980L, runBlocking { referrals.entries("boris").sumOf { it.amountMinor } })
    }

    @Test
    fun `a refund delivered many times at once takes the share back once`() {
        database = open()
        val referrals = ExposedReferralRepository { database }
        runBlocking { referrals.borisEarned(5980) }

        val reversals = concurrently(6) { referrals.reverse("pay-anna", "r-1", start) { accrual, done -> accrual.amountMinor - done } }

        assertEquals(1, reversals.count { it != null }, "reversals: $reversals")
        assertEquals(0L, runBlocking { referrals.entries("boris").sumOf { it.amountMinor } })
    }

    @Test
    fun `an invited user is attached once and earns once`() {
        database = open()
        val referrals = ExposedReferralRepository { database }
        runBlocking { referrals.saveCode("boris", "BRSKCDM2", start) }

        val attached = concurrently(6) { referrals.attach("anna", "boris", "BRSKCDM2", start) }
        val accrued = concurrently(6) { referrals.appendIfAbsent(
            LedgerEntry("boris", LedgerKind.ACCRUAL, "accrual:anna", "pay-$it", "anna", "RUB", 5980,
                availableAt = start, periodDays = null, completed = false, createdAt = start)) }

        assertEquals(1, attached.count { it })
        assertEquals(1, accrued.count { it })
        assertEquals(null, runBlocking { referrals.saveCode("vera", "BRSKCDM2", start) }, "a taken code is refused")
    }

    @Test
    fun `a debit with an earlier clock still sees a later debit`() {
        database = open()
        val referrals = ExposedReferralRepository { database }
        runBlocking {
            referrals.borisEarned(5980)
            assertTrue(referrals.debit("boris", "redeem:boris:late", "RUB", 4000, 7, start) is DebitResult.Debited)
            assertEquals(DebitResult.Insufficient(1980),
                referrals.debit("boris", "redeem:boris:early", "RUB", 4000, 7, start.minus(Duration.ofHours(1))))
        }
    }

    @Test
    fun `a deleted account's balance cannot be spent, even by parallel requests`() {
        database = open()
        val referrals = ExposedReferralRepository { database }
        runBlocking { referrals.borisEarned(5980); referrals.deleteUser("boris", start) }

        val results = concurrently(6) { i -> referrals.debit("boris", "redeem:boris:req-$i", "RUB", 2000, 7, start) }

        assertTrue(results.all { it == DebitResult.AccountClosed }, "results: $results")
        assertEquals(5980L, runBlocking { referrals.entries("boris").sumOf { it.amountMinor } })
        assertEquals(null, runBlocking { referrals.codeOf("boris") })
    }

    @Test
    fun `concurrent partial refunds settle to the share of what was kept`() {
        database = open()
        val entitlementRepo = ExposedEntitlementRepository { database }
        val referrals = ExposedReferralRepository { database }
        val rewards = ReferralRewards(referrals, entitlementRepo, ReferralPolicy(20, Duration.ofDays(30), emptyList())) { start }
        val service = EntitlementService(entitlementRepo, observer = rewards) { start }
        runBlocking {
            referrals.saveCode("boris", "BRSKCDM2", start)
            referrals.attach("anna", "boris", "BRSKCDM2", start)
            service.recordVerifiedPayment(VerifiedPayment("pay-anna", "anna", PaymentSource.YOOKASSA, 29900, 0, "RUB"), "{}", month)
        }

        // 2999 does not divide evenly at 20%: per-refund deltas would leave 1787, the target leaves 1781.
        concurrently(7) { i -> service.applyRefund(VerifiedRefund("r-$i", "pay-anna", 2999, "RUB", full = false, rawPayload = "{}")) }
        concurrently(7) { i -> service.applyRefund(VerifiedRefund("r-$i", "pay-anna", 2999, "RUB", full = false, rawPayload = "{}")) }

        assertEquals((29900L - 7 * 2999) * 20 / 100, runBlocking { referrals.entries("boris").sumOf { it.amountMinor } })
    }
}

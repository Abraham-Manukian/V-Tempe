package com.vtempe.server.features.entitlement.data.db

import com.vtempe.server.config.Env
import com.vtempe.server.features.referral.data.db.ReferralAccounts
import com.vtempe.server.features.referral.data.db.ReferralAttributions
import com.vtempe.server.features.referral.data.db.ReferralCodes
import com.vtempe.server.features.referral.data.db.ReferralLedger
import com.vtempe.server.features.sync.data.db.SyncBlobs
import com.vtempe.server.features.sync.data.db.SyncWriteBarriers
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Where the configured database stands. */
sealed interface DatabaseState {
    /** No DATABASE_URL: local development; callers use in-memory stores. */
    data object Disabled : DatabaseState
    data class Ready(val database: Database) : DatabaseState
    /** Configured, but connecting or creating the schema failed; retried later. */
    data object Unavailable : DatabaseState
}

/** A durable store is configured but cannot be reached; the request answers 503, never a fake success. */
class StorageUnavailableException : IllegalStateException("The configured database is unavailable")

/**
 * The one connection to the configured database (VT-AUD-079). The previous factory marked itself
 * "attempted" before connecting, so a single failed attempt left every later caller with "no
 * database" — and DI then picked in-memory stores that answered 204 and lost the data on restart.
 *
 * Now a configured database is never replaced by memory: until it is [DatabaseState.Ready] the
 * durable repositories throw [StorageUnavailableException]. A failed attempt is retried no more
 * often than every [retryAfterMs]; only a fully initialized database (schema included) is shared,
 * and [open] closes whatever it opened when the schema step fails.
 *
 * DATABASE_URL is a plain JDBC URL; see [fromEnv] for the supported shapes.
 */
class DatabaseConnector(
    private val jdbcUrl: String?,
    private val open: () -> Database,
    private val retryAfterMs: Long = 5_000,
    private val nowMs: () -> Long = System::currentTimeMillis,
    /** How long a request waits for an attempt another request is making once an attempt has
     *  failed: during an outage the others answer 503 instead of queueing behind a connect
     *  timeout, which would tie up request threads (and /ready) for its whole length. */
    private val waitForOpenMs: Long = 2_000,
    /** The same wait before any attempt has failed — a cold start: long enough for the first
     *  open (pool + schema check, bounded by the connection timeout) so its requests are served. */
    private val firstOpenWaitMs: Long = 12_000,
) {
    private val logger = LoggerFactory.getLogger(DatabaseConnector::class.java)
    @Volatile private var ready: Database? = null
    private val opening = ReentrantLock()
    @Volatile private var lastFailureAtMs: Long? = null // written under [opening]

    val isConfigured: Boolean get() = jdbcUrl != null

    fun state(): DatabaseState {
        if (jdbcUrl == null) return DatabaseState.Disabled
        ready?.let { return DatabaseState.Ready(it) } // the common case takes no lock
        val wait = if (lastFailureAtMs == null) firstOpenWaitMs else waitForOpenMs
        val locked = try {
            opening.tryLock(wait, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt() // keep the flag; this request just gets a 503
            false
        }
        if (!locked) return DatabaseState.Unavailable
        try {
            ready?.let { return DatabaseState.Ready(it) }
            val failedAt = lastFailureAtMs
            if (failedAt != null && nowMs() - failedAt < retryAfterMs) return DatabaseState.Unavailable
            return try {
                open().also { ready = it }.let(DatabaseState::Ready).also { logger.info("Database ready") }
            } catch (error: Exception) {
                lastFailureAtMs = nowMs()
                logger.error("Database unavailable ({}); retrying in {} ms", error::class.simpleName, retryAfterMs)
                DatabaseState.Unavailable
            }
        } finally {
            opening.unlock()
        }
    }

    /** The ready database, for durable repositories; throws instead of falling back to memory. */
    fun require(): Database = (state() as? DatabaseState.Ready)?.database ?: throw StorageUnavailableException()

    companion object {
        /**
         * Two supported DATABASE_URL shapes:
         * - Direct TCP (dev/testing only): `jdbc:postgresql://HOST:5432/DBNAME`
         * - Cloud SQL via the Java Connector (production; needs `roles/cloudsql.client`):
         *   `jdbc:postgresql:///DBNAME?cloudSqlInstance=PROJECT:REGION:INSTANCE&socketFactory=com.google.cloud.sql.postgres.SocketFactory`
         *
         * DATABASE_USER / DATABASE_PASSWORD are separate secrets, never embedded in the URL.
         */
        fun fromEnv(): DatabaseConnector {
            val url = Env["DATABASE_URL"]?.takeIf { it.isNotBlank() }
            if (url == null) {
                LoggerFactory.getLogger(DatabaseConnector::class.java)
                    .warn("DATABASE_URL is not set — entitlement/sync use in-memory stores (local development only)")
            }
            return DatabaseConnector(url, open = {
                openPostgres(
                    url = checkNotNull(url),
                    user = Env["DATABASE_USER"],
                    password = Env["DATABASE_PASSWORD"],
                    poolSize = Env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: 5,
                )
            })
        }

        private const val CONNECTION_TIMEOUT_MS = 10_000L

        /** Opens the pool and creates missing tables and columns. The pool lives as long as the server. */
        internal fun openPostgres(url: String, user: String?, password: String?, poolSize: Int): Database =
            openPostgresPool(url, user, password, poolSize).database

        /** [openPostgres] with its pool, for callers that must close it: integration tests open a
         *  throwaway database exactly the way production does, one pool per test. */
        internal fun openPostgresPool(url: String, user: String?, password: String?, poolSize: Int): PostgresPool {
            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = url
                    username = user
                    this.password = password
                    driverClassName = "org.postgresql.Driver"
                    maximumPoolSize = poolSize
                    // Explicit instead of Hikari's 30 s default: an unreachable database fails
                    // an attempt (and a request waiting for a connection) within seconds.
                    connectionTimeout = CONNECTION_TIMEOUT_MS
                }
            )
            return try {
                val database = Database.connect(dataSource).also { database ->
                    transaction(database) {
                        SchemaUtils.createMissingTablesAndColumns(
                            Entitlements, Payments, GrantWindows, PaymentRefunds, SyncBlobs, SyncWriteBarriers,
                            ReferralAccounts, ReferralCodes, ReferralAttributions, ReferralLedger,
                        )
                        // Explicit so it also reaches databases created before it existed: every new
                        // payment reads its earlier refunds by this column, under the payment lock.
                        exec("CREATE INDEX IF NOT EXISTS payment_refunds_payment_external_id_idx ON payment_refunds (payment_external_id)")
                    }
                }
                PostgresPool(database, dataSource)
            } catch (error: Exception) {
                dataSource.close() // no half-initialized pool left behind
                throw error
            }
        }
    }
}

/** A database and the connection pool behind it; [close] gives the connections back to PostgreSQL. */
internal class PostgresPool(val database: Database, private val dataSource: HikariDataSource) : AutoCloseable {
    override fun close() = dataSource.close()
}

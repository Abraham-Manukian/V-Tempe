package com.vtempe.server.features.entitlement.data.db

import com.vtempe.server.config.Env
import com.vtempe.server.features.sync.data.db.SyncBlobs
import com.vtempe.server.features.sync.data.db.SyncWriteBarriers
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

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
) {
    private val logger = LoggerFactory.getLogger(DatabaseConnector::class.java)
    @Volatile private var ready: Database? = null
    private var lastFailureAtMs: Long? = null

    val isConfigured: Boolean get() = jdbcUrl != null

    @Synchronized
    fun state(): DatabaseState {
        if (jdbcUrl == null) return DatabaseState.Disabled
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
            return DatabaseConnector(url, open = { openPostgres(checkNotNull(url)) })
        }

        private fun openPostgres(url: String): Database {
            val dataSource = HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = url
                    username = Env["DATABASE_USER"]
                    password = Env["DATABASE_PASSWORD"]
                    driverClassName = "org.postgresql.Driver"
                    maximumPoolSize = Env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: 5
                }
            )
            return try {
                Database.connect(dataSource).also { database ->
                    transaction(database) {
                        SchemaUtils.createMissingTablesAndColumns(Entitlements, Payments, GrantWindows, PaymentRefunds, SyncBlobs, SyncWriteBarriers)
                    }
                }
            } catch (error: Exception) {
                dataSource.close() // no half-initialized pool left behind
                throw error
            }
        }
    }
}

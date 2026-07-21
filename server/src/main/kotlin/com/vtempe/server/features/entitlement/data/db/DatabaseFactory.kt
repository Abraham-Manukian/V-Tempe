package com.vtempe.server.features.entitlement.data.db

import com.vtempe.server.config.Env
import com.vtempe.server.features.sync.data.db.SyncBlobs
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory

/**
 * Connects to Postgres if DATABASE_URL is configured, otherwise returns null — mirrors how
 * APP_SECRET being unset leaves the app running (with a warning) rather than crashing, so a
 * fresh local checkout still boots without a database provisioned. Callers must fall back to
 * [com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository] when this
 * returns null; entitlement checks are simply never granted until DATABASE_URL is set.
 *
 * DATABASE_URL is a plain JDBC URL, not a Postgres connection string. Two supported shapes:
 *
 * - Direct TCP (public IP, dev/testing only): `jdbc:postgresql://HOST:5432/DBNAME`
 * - Cloud SQL via the Java Connector (recommended for production — no public IP, IAM + mutual
 *   TLS instead of an IP allowlist; requires the `com.google.cloud.sql:postgres-socket-factory`
 *   dependency, already on the classpath, and the Cloud Run service's runtime service account
 *   to have the `roles/cloudsql.client` IAM role):
 *   `jdbc:postgresql:///DBNAME?cloudSqlInstance=PROJECT_ID:REGION:INSTANCE_ID&socketFactory=com.google.cloud.sql.postgres.SocketFactory`
 *   (note the empty host — `postgresql:///`, not `postgresql://host/` — the socket factory
 *   replaces normal TCP networking entirely).
 *
 * DATABASE_USER / DATABASE_PASSWORD are always separate secrets (never embedded in the URL).
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    // Memoized: multiple DI singles (EntitlementRepository, SyncBlobRepository, ...) each call
    // connectOrNull() independently, and without caching that would open a separate HikariCP
    // pool — and re-run the schema check — per caller instead of sharing one connection.
    @Volatile private var attempted = false
    @Volatile private var cached: Database? = null

    @Synchronized
    fun connectOrNull(): Database? {
        if (attempted) return cached
        attempted = true

        val url = Env["DATABASE_URL"]?.takeIf { it.isNotBlank() }
        if (url == null) {
            logger.warn("DATABASE_URL is not set — entitlement/sync will be inert until it's configured")
            return null
        }
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = url
            username = Env["DATABASE_USER"]
            password = Env["DATABASE_PASSWORD"]
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = Env["DATABASE_POOL_SIZE"]?.toIntOrNull() ?: 5
        }
        val database = Database.connect(HikariDataSource(hikariConfig))
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(Entitlements, Payments, SyncBlobs)
        }
        logger.info("Connected to database and verified entitlement/payments/sync schema")
        cached = database
        return database
    }
}

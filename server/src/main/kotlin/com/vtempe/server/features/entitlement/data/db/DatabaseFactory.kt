package com.vtempe.server.features.entitlement.data.db

import com.vtempe.server.config.Env
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
 * DATABASE_URL is a plain JDBC URL (jdbc:postgresql://host:port/db), not a Postgres connection
 * string — Cloud SQL via the Cloud SQL Auth Proxy / Unix socket also works with the standard
 * JDBC socketFactory query param, see Cloud SQL docs when provisioning.
 */
object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun connectOrNull(): Database? {
        val url = Env["DATABASE_URL"]?.takeIf { it.isNotBlank() }
        if (url == null) {
            logger.warn("DATABASE_URL is not set — entitlement checks will deny everyone until it's configured")
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
            SchemaUtils.createMissingTablesAndColumns(Entitlements, Payments)
        }
        logger.info("Connected to database and verified entitlement/payments schema")
        return database
    }
}

package com.vtempe.server.features.entitlement.data.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/** One row per user — the CURRENT entitlement only. History of individual payments lives in
 *  [Payments]; this table is what [com.vtempe.server.features.entitlement.data.repo.ExposedEntitlementRepository]
 *  reads/writes on every request, so it stays small and index-free beyond the primary key. */
object Entitlements : Table("entitlements") {
    val userId = varchar("user_id", 128)
    val expiresAt = timestamp("expires_at")
    // Named paymentSource, not source: `source` clashes with ColumnSet.source (the FROM clause).
    val paymentSource = varchar("source", 32)
    val externalRef = varchar("external_ref", 256).nullable()
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)
}

/** Append-only ledger of individual provider payment notifications. [externalId] is UNIQUE —
 *  the sole idempotency guard against a webhook being delivered more than once (all three
 *  providers — Google RTDN, Apple Server Notifications, YooKassa — can and do redeliver). */
object Payments : Table("payments") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 128)
    val paymentSource = varchar("source", 32)
    val externalId = varchar("external_id", 256).uniqueIndex()
    val amountMinor = long("amount_minor")
    val currency = varchar("currency", 8)
    val rawPayload = text("raw_payload")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

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

/**
 * The access window each payment bought (VT-AUD-052). Unlike the append-only [Payments] ledger it
 * changes: a full refund ends its payment's window at the refund time and moves later windows of
 * the same user up by the removed part. Payments recorded before this table existed have no row.
 */
object GrantWindows : Table("grant_windows") {
    val paymentExternalId = varchar("payment_external_id", 256)
    val userId = varchar("user_id", 128).index()
    val grantedFrom = timestamp("granted_from")
    val grantedUntil = timestamp("granted_until")

    override val primaryKey = PrimaryKey(paymentExternalId)
}

/** Every verified provider refund and what it did to access — kept like [Payments] for audit, and
 *  the idempotency guard for refund notifications ([refundId] is UNIQUE). */
object PaymentRefunds : Table("payment_refunds") {
    val id = long("id").autoIncrement()
    val refundId = varchar("refund_id", 256).uniqueIndex()
    val paymentExternalId = varchar("payment_external_id", 256)
    val userId = varchar("user_id", 128).nullable()
    val amountMinor = long("amount_minor")
    val currency = varchar("currency", 8)
    val outcome = varchar("outcome", 32)
    val removedSeconds = long("removed_seconds")
    val rawPayload = text("raw_payload")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

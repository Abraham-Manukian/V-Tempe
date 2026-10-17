package com.vtempe.server.features.referral.data.db

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * The per-user lock for balance operations (`SELECT ... FOR UPDATE`) and the account's state.
 * Created on first use and never deleted: account deletion only sets [closedAt], so a deleted
 * user's retained balance stays locked and can never be spent.
 */
object ReferralAccounts : Table("referral_accounts") {
    val userId = varchar("user_id", 128)
    val closedAt = timestamp("closed_at").nullable()

    override val primaryKey = PrimaryKey(userId)
}

/** One invite code per user; [code] is UNIQUE. */
object ReferralCodes : Table("referral_codes") {
    val userId = varchar("user_id", 128)
    val code = varchar("code", 16).uniqueIndex()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(userId)
}

/** Who invited whom. The primary key on the invited user makes the first attribution final. */
object ReferralAttributions : Table("referral_attributions") {
    val inviteeUserId = varchar("invitee_user_id", 128)
    val referrerUserId = varchar("referrer_user_id", 128).index()
    val code = varchar("code", 16)
    val attachedAt = timestamp("attached_at")

    override val primaryKey = PrimaryKey(inviteeUserId)
}

/** Balance movements; [sourceRef] is UNIQUE and is the idempotency key of each movement
 *  (see LedgerEntry). Kept on account deletion like the payments ledger. */
object ReferralLedger : Table("referral_ledger") {
    val id = long("id").autoIncrement()
    val userId = varchar("user_id", 128).index()
    val kind = varchar("kind", 16)
    val sourceRef = varchar("source_ref", 320).uniqueIndex()
    val paymentExternalId = varchar("payment_external_id", 256).nullable().index()
    val inviteeUserId = varchar("invitee_user_id", 128).nullable()
    val currency = varchar("currency", 8)
    val amountMinor = long("amount_minor")
    val availableAt = timestamp("available_at")
    val periodDays = integer("period_days").nullable()
    val baseMinor = long("base_minor").nullable()
    val sharePercent = integer("share_percent").nullable()
    val completed = bool("completed")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)
}

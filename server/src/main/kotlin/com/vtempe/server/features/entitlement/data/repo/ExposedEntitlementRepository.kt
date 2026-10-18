package com.vtempe.server.features.entitlement.data.repo

import com.vtempe.server.features.entitlement.data.db.Entitlements
import com.vtempe.server.features.entitlement.data.db.GrantWindows
import com.vtempe.server.features.entitlement.data.db.PaymentRefunds
import com.vtempe.server.features.entitlement.data.db.Payments
import com.vtempe.server.features.entitlement.domain.model.GrantWindow
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.RefundOutcome
import com.vtempe.server.features.entitlement.domain.model.RefundResult
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.entitlement.domain.port.EntitlementRecord
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

class ExposedEntitlementRepository(private val database: () -> Database) : EntitlementRepository {

    override suspend fun find(userId: String): EntitlementRecord? =
        newSuspendedTransaction(Dispatchers.IO, database()) {
            Entitlements.selectAll()
                .where { Entitlements.userId eq userId }
                .singleOrNull()
                ?.let {
                    EntitlementRecord(
                        userId = it[Entitlements.userId],
                        expiresAt = it[Entitlements.expiresAt],
                        // Tolerant: a rollback to this revision must still read rows a newer one wrote.
                        source = PaymentSource.entries.firstOrNull { s -> s.name == it[Entitlements.paymentSource] },
                        externalRef = it[Entitlements.externalRef]
                    )
                }
        }

    override suspend fun grantUntilAtLeast(
        userId: String,
        expiresAt: Instant,
        source: PaymentSource,
        externalRef: String?
    ) {
        newSuspendedTransaction(Dispatchers.IO, database()) {
            grantUntilAtLeastInTransaction(userId, expiresAt, source, externalRef)
        }
    }

    override suspend fun hasPayments(userId: String): Boolean =
        newSuspendedTransaction(Dispatchers.IO, database()) {
            Payments.selectAll().where { Payments.userId eq userId }.limit(1).any()
        }

    override suspend fun refundedMinor(paymentExternalId: String): Long =
        newSuspendedTransaction(Dispatchers.IO, database()) {
            PaymentRefunds.selectAll()
                .where { PaymentRefunds.paymentExternalId eq paymentExternalId }
                .sumOf { it[PaymentRefunds.amountMinor] }
        }

    override suspend fun firstProviderPaymentId(userId: String): String? =
        newSuspendedTransaction(Dispatchers.IO, database()) {
            Payments.selectAll()
                .where { (Payments.userId eq userId) and (Payments.paymentSource neq PaymentSource.REFERRAL_BALANCE.name) }
                .orderBy(Payments.id)
                .limit(1)
                .singleOrNull()?.get(Payments.externalId)
        }

    override suspend fun revoke(userId: String) {
        newSuspendedTransaction(Dispatchers.IO, database()) {
            Entitlements.deleteWhere { Entitlements.userId eq userId }
        }
    }

    override suspend fun recordPaymentAndExtend(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        refundedMinor: Long,
        now: Instant,
        grant: (currentExpiry: Instant?) -> GrantWindow
    ): Instant? = newSuspendedTransaction(Dispatchers.IO, database()) {
        lockPayment(externalId)
        // insertIgnore, not insert + catch: a failed statement aborts a PostgreSQL transaction, so
        // committing after a caught unique violation would rely on driver leniency.
        val isNewPayment = Payments.insertIgnore {
            it[Payments.userId] = userId
            it[Payments.paymentSource] = source.name
            it[Payments.externalId] = externalId
            it[Payments.amountMinor] = amountMinor
            it[Payments.currency] = currency
            it[Payments.rawPayload] = rawPayload
            it[Payments.createdAt] = Instant.now()
        }.insertedCount > 0
        if (!isNewPayment) return@newSuspendedTransaction null
        // Same transaction as the payment insert above: if the grant step below throws, the
        // whole transaction rolls back INCLUDING the payment insert, so a retried webhook
        // delivery sees no recorded payment and reprocesses cleanly from scratch — never
        // "marked processed, but never actually granted".
        val (expiresAt, window) = extendInTransaction(userId, source, externalId, grant)
        expiresAt.minus(settleEarlierRefunds(userId, externalId, amountMinor, refundedMinor, window, now))
    }

    override suspend fun recordRefund(refund: VerifiedRefund, now: Instant): RefundResult =
        newSuspendedTransaction(Dispatchers.IO, database()) {
            lockPayment(refund.paymentExternalId)
            // The refund row first: a redelivered refund conflicts on refund_id and changes nothing,
            // and a concurrent duplicate waits on the unique key until this one commits.
            val isNewRefund = PaymentRefunds.insertIgnore {
                it[refundId] = refund.refundId
                it[paymentExternalId] = refund.paymentExternalId
                it[amountMinor] = refund.amountMinor
                it[currency] = refund.currency
                it[outcome] = RefundOutcome.DUPLICATE.name // placeholder, replaced below
                it[removedSeconds] = 0
                it[rawPayload] = refund.rawPayload
                it[createdAt] = now
            }.insertedCount > 0
            if (!isNewRefund) return@newSuspendedTransaction RefundResult(RefundOutcome.DUPLICATE)

            val userId = Payments.selectAll()
                .where { Payments.externalId eq refund.paymentExternalId }
                .singleOrNull()?.get(Payments.userId)
            // Same lock order as a payment (entitlement row first), so a payment and a refund of
            // one user apply one after the other.
            userId?.let { lockEntitlement(it) }
            val window = GrantWindows.selectAll()
                .where { GrantWindows.paymentExternalId eq refund.paymentExternalId }
                .singleOrNull()
                ?.let { GrantWindow(it[GrantWindows.grantedFrom], it[GrantWindows.grantedUntil]) }
            val result = when {
                userId == null -> RefundResult(RefundOutcome.UNMATCHED_REVIEW)
                !refund.full -> RefundResult(RefundOutcome.PARTIAL_REVIEW, userId)
                window == null -> RefundResult(RefundOutcome.UNMATCHED_REVIEW, userId)
                else -> takeBack(userId, refund.paymentExternalId, RefundCut.of(window, now))
            }
            PaymentRefunds.update({ PaymentRefunds.refundId eq refund.refundId }) {
                it[PaymentRefunds.userId] = result.userId
                it[outcome] = result.outcome.name
                it[removedSeconds] = result.removed.seconds
            }
            result
        }

    private fun Transaction.lockEntitlement(userId: String) {
        Entitlements.selectAll().where { Entitlements.userId eq userId }.forUpdate().singleOrNull()
    }

    /**
     * Serializes a payment and the refunds of the same provider payment id. Without it, a refund
     * committing while its payment's transaction is still open would see no payment (stored as
     * unmatched) and the payment would see no refund — the full period stays granted. Taken first
     * in both transactions, before any row lock. The two-key form has its own key space, apart
     * from the per-user sync locks. The key is taken from SHA-256, so a client-chosen id (referral
     * redemptions) cannot aim at another payment's lock; only ints are interpolated, cast so that
     * Int.MIN_VALUE is not read as a bigint literal.
     */
    private fun Transaction.lockPayment(paymentExternalId: String) {
        val key = ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(paymentExternalId.toByteArray())).int
        exec("SELECT pg_advisory_xact_lock($PAYMENT_LOCK_SPACE, CAST($key AS integer))")
    }

    /**
     * Applies refunds verified before this payment was recorded (see the port's kdoc). They were
     * stored as unmatched; now that the payment and its [window] exist they get their outcome.
     * Returns how much access was taken back.
     */
    private fun Transaction.settleEarlierRefunds(
        userId: String,
        paymentId: String,
        amountMinor: Long,
        providerRefundedMinor: Long,
        window: GrantWindow,
        now: Instant,
    ): Duration {
        val earlier = PaymentRefunds.selectAll()
            .where { PaymentRefunds.paymentExternalId eq paymentId }
            .orderBy(PaymentRefunds.id)
            .map { it[PaymentRefunds.refundId] to it[PaymentRefunds.amountMinor] }
        val refunded = maxOf(providerRefundedMinor, earlier.sumOf { it.second })
        if (refunded <= 0) return Duration.ZERO
        val result = if (isFullRefund(refunded, amountMinor)) {
            takeBack(userId, paymentId, RefundCut.of(window, now))
        } else {
            RefundResult(RefundOutcome.PARTIAL_REVIEW, userId)
        }
        earlier.forEachIndexed { index, (refundId, _) ->
            PaymentRefunds.update({ PaymentRefunds.refundId eq refundId }) {
                it[PaymentRefunds.userId] = userId
                it[outcome] = result.outcome.name
                // Counted once, on the refund that completed the total.
                it[removedSeconds] = if (index == earlier.lastIndex) result.removed.seconds else 0
            }
        }
        return result.removed
    }

    private fun Transaction.takeBack(userId: String, paymentId: String, cut: RefundCut): RefundResult {
        if (!cut.removed.isZero) {
            val refunded = cut.refundedWindow
            GrantWindows.update({ GrantWindows.paymentExternalId eq paymentId }) {
                it[grantedUntil] = refunded.until
            }
            GrantWindows.selectAll()
                .where { GrantWindows.userId eq userId }
                .map { it[GrantWindows.paymentExternalId] to GrantWindow(it[GrantWindows.grantedFrom], it[GrantWindows.grantedUntil]) }
                .filter { (id, window) -> id != paymentId && cut.movesUp(window) }
                .forEach { (id, window) ->
                    val moved = cut.movedUp(window)
                    GrantWindows.update({ GrantWindows.paymentExternalId eq id }) {
                        it[grantedFrom] = moved.from
                        it[grantedUntil] = moved.until
                    }
                }
            Entitlements.selectAll().where { Entitlements.userId eq userId }.singleOrNull()?.let { row ->
                Entitlements.update({ Entitlements.userId eq userId }) {
                    it[expiresAt] = row[Entitlements.expiresAt].minus(cut.removed)
                    it[updatedAt] = Instant.now()
                }
            }
        }
        return RefundResult(RefundOutcome.ACCESS_REDUCED, userId, cut.removed)
    }

    /**
     * Reads the current expiry under a row lock and writes the extension in the same transaction
     * (VT-AUD-051). A concurrent payment for the same user blocks on `SELECT ... FOR UPDATE`
     * until this one commits, then reads the extended value. The first payment of a user creates
     * the row with `INSERT ... ON CONFLICT DO NOTHING`; a concurrent first insert waits on the
     * unique key, so both still end up extending one row in turn.
     */
    private fun Transaction.extendInTransaction(
        userId: String,
        source: PaymentSource,
        externalRef: String,
        grant: (currentExpiry: Instant?) -> GrantWindow,
    ): Pair<Instant, GrantWindow> {
        val created = Entitlements.insertIgnore {
            it[Entitlements.userId] = userId
            it[Entitlements.expiresAt] = Instant.EPOCH // placeholder, replaced below
            it[Entitlements.paymentSource] = source.name
            it[Entitlements.externalRef] = externalRef
            it[Entitlements.updatedAt] = Instant.now()
        }.insertedCount > 0
        val stored = Entitlements.selectAll()
            .where { Entitlements.userId eq userId }
            .forUpdate()
            .single()[Entitlements.expiresAt]
        val current = if (created) null else stored
        val window = grant(current)
        GrantWindows.insert {
            it[paymentExternalId] = externalRef
            it[GrantWindows.userId] = userId
            it[grantedFrom] = window.from
            it[grantedUntil] = window.until
        }
        val target = if (current != null && current > window.until) current else window.until
        Entitlements.update({ Entitlements.userId eq userId }) {
            it[Entitlements.expiresAt] = target
            it[Entitlements.paymentSource] = source.name
            it[Entitlements.externalRef] = externalRef
            it[Entitlements.updatedAt] = Instant.now()
        }
        return target to window
    }

    /**
     * `INSERT ... ON CONFLICT DO NOTHING` (via [insertIgnore]) followed by a guarded
     * `UPDATE ... WHERE expires_at < newExpiresAt`. Deliberately NOT "try insert, catch unique
     * violation, retry the update" — Postgres aborts the entire enclosing transaction on any
     * failed statement, so a caught-and-retried statement inside the SAME transaction would
     * itself fail with "current transaction is aborted" without a SAVEPOINT. `insertIgnore`
     * sidesteps this by never throwing on conflict at the SQL level in the first place.
     *
     * Whether the row already existed or was just created, the guarded UPDATE is the single
     * source of truth for the final value — it never shortens an existing later expiry (see
     * EntitlementRepository kdoc), and Postgres's row-level locking under concurrent UPDATEs to
     * the same row means two simultaneous grants can't race each other into a wrong result.
     */
    private fun Transaction.grantUntilAtLeastInTransaction(
        userId: String,
        expiresAt: Instant,
        source: PaymentSource,
        externalRef: String?
    ) {
        Entitlements.insertIgnore {
            it[Entitlements.userId] = userId
            it[Entitlements.expiresAt] = expiresAt
            it[Entitlements.paymentSource] = source.name
            it[Entitlements.externalRef] = externalRef
            it[Entitlements.updatedAt] = Instant.now()
        }
        Entitlements.update({
            (Entitlements.userId eq userId) and (Entitlements.expiresAt less expiresAt)
        }) {
            it[Entitlements.expiresAt] = expiresAt
            it[Entitlements.paymentSource] = source.name
            it[Entitlements.externalRef] = externalRef
            it[Entitlements.updatedAt] = Instant.now()
        }
    }

    private companion object {
        /** Advisory-lock key space of payment ids (see [lockPayment]). */
        const val PAYMENT_LOCK_SPACE = 4207
    }
}

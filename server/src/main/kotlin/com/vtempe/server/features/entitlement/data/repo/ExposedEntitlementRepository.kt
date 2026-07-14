package com.vtempe.server.features.entitlement.data.repo

import com.vtempe.server.features.entitlement.data.db.Entitlements
import com.vtempe.server.features.entitlement.data.db.Payments
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.port.EntitlementRecord
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.sql.SQLException
import java.time.Instant

class ExposedEntitlementRepository(private val database: Database) : EntitlementRepository {

    override suspend fun find(userId: String): EntitlementRecord? =
        newSuspendedTransaction(Dispatchers.IO, database) {
            Entitlements.selectAll()
                .where { Entitlements.userId eq userId }
                .singleOrNull()
                ?.let {
                    EntitlementRecord(
                        userId = it[Entitlements.userId],
                        expiresAt = it[Entitlements.expiresAt],
                        source = PaymentSource.valueOf(it[Entitlements.paymentSource]),
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
        newSuspendedTransaction(Dispatchers.IO, database) {
            grantUntilAtLeastInTransaction(userId, expiresAt, source, externalRef)
        }
    }

    override suspend fun revoke(userId: String) {
        newSuspendedTransaction(Dispatchers.IO, database) {
            Entitlements.deleteWhere { Entitlements.userId eq userId }
        }
    }

    override suspend fun recordPaymentAndGrant(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        expiresAt: Instant
    ): Boolean = newSuspendedTransaction(Dispatchers.IO, database) {
        val isNewPayment = try {
            Payments.insert {
                it[Payments.userId] = userId
                it[Payments.paymentSource] = source.name
                it[Payments.externalId] = externalId
                it[Payments.amountMinor] = amountMinor
                it[Payments.currency] = currency
                it[Payments.rawPayload] = rawPayload
                it[Payments.createdAt] = Instant.now()
            }
            true
        } catch (e: ExposedSQLException) {
            if (isUniqueViolation(e)) false else throw e
        }
        // Same transaction as the payment insert above: if the grant step below throws, the
        // whole transaction rolls back INCLUDING the payment insert, so a retried webhook
        // delivery sees no recorded payment and reprocesses cleanly from scratch — never
        // "marked processed, but never actually granted".
        if (isNewPayment) {
            grantUntilAtLeastInTransaction(userId, expiresAt, source, externalId)
        }
        isNewPayment
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

    private fun isUniqueViolation(e: ExposedSQLException): Boolean {
        var cause: Throwable? = e
        while (cause != null) {
            if (cause is SQLException && cause.sqlState == "23505") return true
            cause = cause.cause
        }
        return false
    }
}

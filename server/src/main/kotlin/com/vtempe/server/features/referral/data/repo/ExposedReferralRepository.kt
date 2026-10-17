package com.vtempe.server.features.referral.data.repo

import com.vtempe.server.features.referral.data.db.ReferralAccounts
import com.vtempe.server.features.referral.data.db.ReferralAttributions
import com.vtempe.server.features.referral.data.db.ReferralCodes
import com.vtempe.server.features.referral.data.db.ReferralLedger
import com.vtempe.server.features.referral.domain.model.Attribution
import com.vtempe.server.features.referral.domain.model.DebitResult
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import com.vtempe.server.features.referral.domain.model.LedgerKind
import com.vtempe.server.features.referral.domain.model.availableMinor
import com.vtempe.server.features.referral.domain.model.reversalMinor
import com.vtempe.server.features.referral.domain.port.ReferralRepository
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.time.Instant

class ExposedReferralRepository(private val database: () -> Database) : ReferralRepository {

    private suspend fun <T> tx(block: Transaction.() -> T): T = newSuspendedTransaction(Dispatchers.IO, database()) { block() }

    override suspend fun codeOf(userId: String): String? = tx {
        ReferralCodes.selectAll().where { ReferralCodes.userId eq userId }.singleOrNull()?.get(ReferralCodes.code)
    }

    override suspend fun saveCode(userId: String, code: String, now: Instant): String? = tx {
        // ON CONFLICT DO NOTHING covers both keys: the user already has a code, or the code is taken.
        ReferralCodes.insertIgnore {
            it[ReferralCodes.userId] = userId
            it[ReferralCodes.code] = code
            it[createdAt] = now
        }
        ReferralCodes.selectAll().where { ReferralCodes.userId eq userId }.singleOrNull()?.get(ReferralCodes.code)
    }

    override suspend fun ownerOfCode(code: String): String? = tx {
        ReferralCodes.selectAll().where { ReferralCodes.code eq code }.singleOrNull()?.get(ReferralCodes.userId)
    }

    override suspend fun attach(inviteeId: String, referrerId: String, code: String, now: Instant): Boolean = tx {
        ReferralAttributions.insertIgnore {
            it[inviteeUserId] = inviteeId
            it[referrerUserId] = referrerId
            it[ReferralAttributions.code] = code
            it[attachedAt] = now
        }.insertedCount > 0
    }

    override suspend fun attributionOf(inviteeId: String): Attribution? = tx {
        ReferralAttributions.selectAll().where { ReferralAttributions.inviteeUserId eq inviteeId }.singleOrNull()?.let {
            Attribution(it[ReferralAttributions.inviteeUserId], it[ReferralAttributions.referrerUserId],
                it[ReferralAttributions.code], it[ReferralAttributions.attachedAt])
        }
    }

    override suspend fun invitedCount(referrerId: String): Int = tx {
        ReferralAttributions.selectAll().where { ReferralAttributions.referrerUserId eq referrerId }.count().toInt()
    }

    override suspend fun appendIfAbsent(entry: LedgerEntry): Boolean = tx { insertEntry(entry) }

    override suspend fun reverse(
        paymentExternalId: String,
        refundId: String,
        now: Instant,
        amountFor: (accrual: LedgerEntry, alreadyReversedMinor: Long) -> Long,
    ): LedgerEntry? = tx {
        val accrual = ReferralLedger.selectAll()
            .where { (ReferralLedger.paymentExternalId eq paymentExternalId) and (ReferralLedger.kind eq LedgerKind.ACCRUAL.name) }
            .singleOrNull()?.toEntry() ?: return@tx null
        lockAccount(accrual.userId)
        val sourceRef = "reversal:$refundId"
        if (exists(sourceRef)) return@tx null
        val reversed = -ReferralLedger.selectAll()
            .where { (ReferralLedger.paymentExternalId eq paymentExternalId) and (ReferralLedger.kind eq LedgerKind.REVERSAL.name) }
            .sumOf { it[ReferralLedger.amountMinor] }
        val amount = reversalMinor(accrual.amountMinor, reversed, amountFor(accrual, reversed))
        if (amount == 0L) return@tx null
        val reversal = accrual.copy(kind = LedgerKind.REVERSAL, sourceRef = sourceRef, amountMinor = -amount, createdAt = now,
            baseMinor = null, sharePercent = null)
        if (insertEntry(reversal)) reversal else null
    }

    override suspend fun debit(
        userId: String,
        sourceRef: String,
        currency: String,
        amountMinor: Long,
        periodDays: Int,
        now: Instant,
    ): DebitResult = tx {
        // Closed first: even a retry of an earlier debit must not grant access to a deleted account.
        if (lockAccount(userId)) return@tx DebitResult.AccountClosed
        ReferralLedger.selectAll().where { ReferralLedger.sourceRef eq sourceRef }.singleOrNull()?.let {
            return@tx DebitResult.AlreadyRecorded(it.toEntry())
        }
        val entries = ReferralLedger.selectAll()
            .where { (ReferralLedger.userId eq userId) and (ReferralLedger.currency eq currency) }
            .map { it.toEntry() }
        val available = availableMinor(entries, currency, now)
        if (available < amountMinor) return@tx DebitResult.Insufficient(available)
        val entry = LedgerEntry(userId, LedgerKind.REDEMPTION, sourceRef, null, null, currency, -amountMinor,
            availableAt = now, periodDays = periodDays, completed = false, createdAt = now)
        insertEntry(entry)
        DebitResult.Debited(entry)
    }

    override suspend fun entries(userId: String): List<LedgerEntry> = tx {
        ReferralLedger.selectAll().where { ReferralLedger.userId eq userId }.orderBy(ReferralLedger.id).map { it.toEntry() }
    }

    override suspend fun markCompleted(sourceRef: String) {
        tx { ReferralLedger.update({ ReferralLedger.sourceRef eq sourceRef }) { it[completed] = true } }
    }

    override suspend fun isClosed(userId: String): Boolean = tx {
        ReferralAccounts.selectAll().where { ReferralAccounts.userId eq userId }.singleOrNull()
            ?.get(ReferralAccounts.closedAt) != null
    }

    override suspend fun deleteUser(userId: String, now: Instant) {
        tx {
            // Same lock as debit, so a redemption racing the deletion either finishes first or sees it.
            lockAccount(userId)
            ReferralAccounts.update({ (ReferralAccounts.userId eq userId) and ReferralAccounts.closedAt.isNull() }) {
                it[closedAt] = now
            }
            ReferralCodes.deleteWhere { ReferralCodes.userId eq userId }
            ReferralAttributions.deleteWhere {
                (ReferralAttributions.inviteeUserId eq userId) or (ReferralAttributions.referrerUserId eq userId)
            }
        }
    }

    /** Creates the user's account row if needed and locks it until the transaction ends. Returns
     *  whether the account is closed. */
    private fun Transaction.lockAccount(userId: String): Boolean {
        ReferralAccounts.insertIgnore { it[ReferralAccounts.userId] = userId }
        return ReferralAccounts.selectAll().where { ReferralAccounts.userId eq userId }.forUpdate().single()[ReferralAccounts.closedAt] != null
    }

    private fun Transaction.exists(sourceRef: String): Boolean =
        ReferralLedger.selectAll().where { ReferralLedger.sourceRef eq sourceRef }.limit(1).any()

    private fun Transaction.insertEntry(entry: LedgerEntry): Boolean = ReferralLedger.insertIgnore {
        it[userId] = entry.userId
        it[kind] = entry.kind.name
        it[sourceRef] = entry.sourceRef
        it[paymentExternalId] = entry.paymentExternalId
        it[inviteeUserId] = entry.inviteeId
        it[currency] = entry.currency
        it[amountMinor] = entry.amountMinor
        it[availableAt] = entry.availableAt
        it[periodDays] = entry.periodDays
        it[baseMinor] = entry.baseMinor
        it[sharePercent] = entry.sharePercent
        it[completed] = entry.completed
        it[createdAt] = entry.createdAt
    }.insertedCount > 0

    private fun ResultRow.toEntry() = LedgerEntry(
        userId = this[ReferralLedger.userId],
        kind = LedgerKind.valueOf(this[ReferralLedger.kind]),
        sourceRef = this[ReferralLedger.sourceRef],
        paymentExternalId = this[ReferralLedger.paymentExternalId],
        inviteeId = this[ReferralLedger.inviteeUserId],
        currency = this[ReferralLedger.currency],
        amountMinor = this[ReferralLedger.amountMinor],
        availableAt = this[ReferralLedger.availableAt],
        periodDays = this[ReferralLedger.periodDays],
        baseMinor = this[ReferralLedger.baseMinor],
        sharePercent = this[ReferralLedger.sharePercent],
        completed = this[ReferralLedger.completed],
        createdAt = this[ReferralLedger.createdAt],
    )
}

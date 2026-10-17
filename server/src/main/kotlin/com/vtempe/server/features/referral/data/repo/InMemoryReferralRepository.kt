package com.vtempe.server.features.referral.data.repo

import com.vtempe.server.features.referral.domain.model.Attribution
import com.vtempe.server.features.referral.domain.model.DebitResult
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import com.vtempe.server.features.referral.domain.model.LedgerKind
import com.vtempe.server.features.referral.domain.model.availableMinor
import com.vtempe.server.features.referral.domain.model.reversalMinor
import com.vtempe.server.features.referral.domain.port.ReferralRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

/**
 * Unit tests and the no-DATABASE_URL development fallback (like InMemoryEntitlementRepository).
 * One lock for everything gives the same guarantees as the per-user account lock of
 * [ExposedReferralRepository]; the balance rules come from the same domain functions.
 */
class InMemoryReferralRepository : ReferralRepository {
    private val codes = HashMap<String, String>() // userId -> code
    private val owners = HashMap<String, String>() // code -> userId
    private val attributions = HashMap<String, Attribution>() // inviteeId -> attribution
    private val ledger = ArrayList<LedgerEntry>()
    private val closed = HashSet<String>()
    private val lock = Mutex()

    override suspend fun codeOf(userId: String): String? = lock.withLock { codes[userId] }

    override suspend fun saveCode(userId: String, code: String, now: Instant): String? = lock.withLock {
        codes[userId]?.let { return@withLock it }
        if (code in owners) return@withLock null
        codes[userId] = code
        owners[code] = userId
        code
    }

    override suspend fun ownerOfCode(code: String): String? = lock.withLock { owners[code] }

    override suspend fun attach(inviteeId: String, referrerId: String, code: String, now: Instant): Boolean =
        lock.withLock { attributions.putIfAbsent(inviteeId, Attribution(inviteeId, referrerId, code, now)) == null }

    override suspend fun attributionOf(inviteeId: String): Attribution? = lock.withLock { attributions[inviteeId] }

    override suspend fun invitedCount(referrerId: String): Int = lock.withLock {
        attributions.values.count { it.referrerId == referrerId }
    }

    override suspend fun appendIfAbsent(entry: LedgerEntry): Boolean = lock.withLock { addIfAbsent(entry) }

    override suspend fun reverse(
        paymentExternalId: String,
        refundId: String,
        now: Instant,
        amountFor: (accrual: LedgerEntry, alreadyReversedMinor: Long) -> Long,
    ): LedgerEntry? = lock.withLock {
        val accrual = ledger.firstOrNull { it.paymentExternalId == paymentExternalId && it.kind == LedgerKind.ACCRUAL }
            ?: return@withLock null
        val sourceRef = "reversal:$refundId"
        if (ledger.any { it.sourceRef == sourceRef }) return@withLock null
        val reversed = -ledger.filter { it.paymentExternalId == paymentExternalId && it.kind == LedgerKind.REVERSAL }
            .sumOf { it.amountMinor }
        val amount = reversalMinor(accrual.amountMinor, reversed, amountFor(accrual, reversed))
        if (amount == 0L) return@withLock null
        accrual.copy(kind = LedgerKind.REVERSAL, sourceRef = sourceRef, amountMinor = -amount, createdAt = now)
            .also { addIfAbsent(it) }
    }

    override suspend fun debit(
        userId: String,
        sourceRef: String,
        currency: String,
        amountMinor: Long,
        periodDays: Int,
        now: Instant,
    ): DebitResult = lock.withLock {
        if (userId in closed) return@withLock DebitResult.AccountClosed
        ledger.firstOrNull { it.sourceRef == sourceRef }?.let { return@withLock DebitResult.AlreadyRecorded(it) }
        val available = availableMinor(ledger.filter { it.userId == userId }, currency, now)
        if (available < amountMinor) return@withLock DebitResult.Insufficient(available)
        val entry = LedgerEntry(userId, LedgerKind.REDEMPTION, sourceRef, null, null, currency, -amountMinor,
            availableAt = now, periodDays = periodDays, completed = false, createdAt = now)
        ledger.add(entry)
        DebitResult.Debited(entry)
    }

    override suspend fun entries(userId: String): List<LedgerEntry> = lock.withLock { ledger.filter { it.userId == userId } }

    override suspend fun markCompleted(sourceRef: String) {
        lock.withLock {
            val index = ledger.indexOfFirst { it.sourceRef == sourceRef }
            if (index >= 0) ledger[index] = ledger[index].copy(completed = true)
        }
    }

    override suspend fun isClosed(userId: String): Boolean = lock.withLock { userId in closed }

    override suspend fun deleteUser(userId: String, now: Instant) {
        lock.withLock {
            closed.add(userId)
            codes.remove(userId)?.let { owners.remove(it) }
            attributions.values.removeIf { it.inviteeId == userId || it.referrerId == userId }
        }
    }

    private fun addIfAbsent(entry: LedgerEntry): Boolean =
        if (ledger.any { it.sourceRef == entry.sourceRef }) false else ledger.add(entry)
}

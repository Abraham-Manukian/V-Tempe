package com.vtempe.server

import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.GrantWindow
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.entitlement.domain.port.EntitlementRepository
import com.vtempe.server.features.referral.data.repo.InMemoryReferralRepository
import com.vtempe.server.features.referral.data.service.ReferralRewards
import com.vtempe.server.features.referral.data.service.ReferralService
import com.vtempe.server.features.referral.domain.model.AttachResult
import com.vtempe.server.features.referral.domain.model.IssueCodeResult
import com.vtempe.server.features.referral.domain.model.RedeemOffer
import com.vtempe.server.features.referral.domain.model.ReferralBalance
import com.vtempe.server.features.referral.domain.model.ReferralPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.Duration
import java.time.Instant

/** An entitlement store whose payment recording can be made to fail once (a crash between steps). */
class FlakyEntitlementRepository(
    private val delegate: InMemoryEntitlementRepository = InMemoryEntitlementRepository(),
) : EntitlementRepository by delegate {
    var failNextPayment = false
    /** Runs once, right before the next payment is recorded (to interleave another operation). */
    var beforeNextPayment: (suspend () -> Unit)? = null

    override suspend fun recordPaymentAndExtend(
        externalId: String,
        userId: String,
        source: PaymentSource,
        amountMinor: Long,
        currency: String,
        rawPayload: String,
        refundedMinor: Long,
        now: Instant,
        grant: (currentExpiry: Instant?) -> GrantWindow,
    ): Instant? {
        beforeNextPayment?.let { hook ->
            beforeNextPayment = null
            hook()
        }
        if (failNextPayment) {
            failNextPayment = false
            error("simulated crash before the access grant")
        }
        return delegate.recordPaymentAndExtend(externalId, userId, source, amountMinor, currency, rawPayload, refundedMinor, now, grant)
    }
}

/**
 * The real referral wiring on in-memory stores, with a controllable clock: boris subscribes and
 * invites anna. Owner's rules: 20% of the first payment, held 30 days; one week costs 2000 RUB
 * (a 500 USD offer exists to test currency handling).
 */
class ReferralWorld {
    var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    val month: Duration = Duration.ofDays(30)
    val entitlementRepo = FlakyEntitlementRepository()
    val referrals = InMemoryReferralRepository()
    val policy = ReferralPolicy(20, Duration.ofDays(30), listOf(RedeemOffer("RUB", 2000, 7), RedeemOffer("USD", 500, 7)))
    val rewards = ReferralRewards(referrals, entitlementRepo, policy) { now }
    val entitlements = EntitlementService(entitlementRepo, observer = rewards) { now }
    private val codes = ArrayDeque(listOf("BRSKCDM2", "VERAAAA3", "GHJKMNP4", "DMTRYYY5"))
    var nextCode: () -> String = { codes.removeFirst().also(codes::addLast) }
    val service = ReferralService(referrals, entitlements, policy, newCode = { nextCode() }) { now }

    suspend fun pay(user: String, id: String, amount: Long = 29900, refunded: Long = 0, currency: String = "RUB",
                    source: PaymentSource = PaymentSource.YOOKASSA) =
        entitlements.recordVerifiedPayment(VerifiedPayment(id, user, source, amount, refunded, currency), "{}", month)

    /** Through EntitlementService, like a provider webhook: recorded first, then observed. */
    suspend fun refund(id: String, payment: String, amount: Long, full: Boolean, providerTotal: Long = 0) =
        entitlements.applyRefund(VerifiedRefund(id, payment, amount, "RUB", full, "{}", paymentRefundedMinor = providerTotal))

    suspend fun codeOf(user: String): String = (service.issueCode(user) as IssueCodeResult.Issued).code

    /** boris subscribes and gets a code; anna enters it. */
    suspend fun borisInvitesAnna(): String {
        pay("boris", "boris-1")
        return codeOf("boris").also { assertEquals(AttachResult.ATTACHED, service.attach("anna", it)) }
    }

    suspend fun balance(user: String, currency: String = "RUB"): ReferralBalance? =
        service.summary(user).balances.singleOrNull { it.currency == currency }

    fun advance(duration: Duration) {
        now = now.plus(duration)
    }
}

package com.vtempe.server

import com.vtempe.server.features.entitlement.domain.model.GrantWindow
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.entitlement.domain.model.VerifiedPayment
import com.vtempe.server.features.entitlement.domain.model.VerifiedRefund
import com.vtempe.server.features.referral.data.service.ReferralRewards
import com.vtempe.server.features.referral.domain.model.LedgerEntry
import com.vtempe.server.features.referral.domain.model.LedgerKind
import com.vtempe.server.features.referral.domain.port.ReferralRepository
import com.vtempe.server.features.referral.domain.model.AttachResult
import com.vtempe.server.features.referral.domain.model.ReferralBalance
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration

/** Which payments earn the referrer a share, and how refunds take it back. */
class ReferralRewardsTest {
    private val w = ReferralWorld()

    @Test
    fun `the first payment earns 20 percent, pending for exactly 30 days`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        assertEquals(ReferralBalance("RUB", 0, 5980), w.balance("boris"))

        w.advance(Duration.ofDays(30).minusSeconds(1))
        assertEquals(ReferralBalance("RUB", 0, 5980), w.balance("boris"), "one second before the hold ends")
        w.advance(Duration.ofSeconds(1))
        assertEquals(ReferralBalance("RUB", 5980, 0), w.balance("boris"))
        assertEquals(1, w.service.summary("boris").rewardedCount)
        assertEquals(1, w.service.summary("boris").invitedCount)
    }

    @Test
    fun `later payments, redeliveries, balance payments and strangers earn nothing`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        w.pay("anna", "anna-1") // redelivery
        w.pay("anna", "anna-2")
        w.pay("anna", "anna-3", amount = 99900)
        w.pay("anna", "anna-redeem", source = PaymentSource.REFERRAL_BALANCE)
        w.pay("gleb", "gleb-1") // nobody invited gleb
        assertEquals(ReferralBalance("RUB", 0, 5980), w.balance("boris"))
    }

    @Test
    fun `a payment recorded before the code was entered means no later payment ever earns`() = runBlocking {
        // The race: anna's first payment lands while she is entering the code, so the attach check
        // saw no payment and the reward step saw no attribution. Simulated by storing the
        // attribution after the payment.
        w.pay("boris", "boris-1")
        val code = w.codeOf("boris")
        w.pay("anna", "anna-1")
        w.referrals.attach("anna", "boris", code, w.now)

        w.pay("anna", "anna-2")
        assertEquals(null, w.balance("boris"), "the second payment never earns")
        w.pay("anna", "anna-1") // redelivery of the first payment now finds the attribution
        assertEquals(ReferralBalance("RUB", 0, 5980), w.balance("boris"), "the first payment is still the one that earns")
    }

    @Test
    fun `a first payment worth no share does not let the second one earn`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-tiny", amount = 4) // 20% of 4 kopecks rounds down to 0
        w.pay("anna", "anna-2")
        assertEquals(null, w.balance("boris"))
    }

    @Test
    fun `a payment already refunded when it is processed earns only on what was kept`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1", refunded = 29900) // refunded before the reward step ran
        assertEquals(null, w.balance("boris"))

        val other = ReferralWorld()
        other.borisInvitesAnna()
        other.pay("anna", "anna-1", refunded = 10000)
        assertEquals(ReferralBalance("RUB", 0, 3980), other.balance("boris"))
    }

    @Test
    fun `a full refund during the hold cancels the share once`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        w.advance(Duration.ofDays(5))
        w.refund("r1", "anna-1", 29900, full = true)
        w.refund("r1", "anna-1", 29900, full = true) // redelivery
        assertEquals(ReferralBalance("RUB", 0, 0), w.balance("boris"))
        assertEquals(0, w.service.summary("boris").rewardedCount)
    }

    @Test
    fun `partial refunds take back the same share and never more than was earned`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1", amount = 499) // share 99
        w.refund("r0", "anna-1", 1, full = false) // share of 1 kopeck is 0: nothing recorded
        w.refund("r1", "anna-1", 100, full = false) // takes 20
        assertEquals(ReferralBalance("RUB", 0, 79), w.balance("boris"))
        assertEquals(1, w.service.summary("boris").rewardedCount, "still rewarded after a partial refund")
        w.refund("r2", "anna-1", 398, full = true)
        w.refund("r3", "anna-1", 499, full = true) // a further refund finds nothing left
        assertEquals(ReferralBalance("RUB", 0, 0), w.balance("boris"))
    }

    @Test
    fun `refunds of payments that earned nothing change nothing`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        w.pay("anna", "anna-2")
        w.refund("r2", "anna-2", 29900, full = true) // the second payment never earned
        w.refund("rx", "unknown-payment", 29900, full = true)
        assertEquals(ReferralBalance("RUB", 0, 5980), w.balance("boris"))
    }

    @Test
    fun `a refund after the hold lowers the available balance below zero`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        w.advance(Duration.ofDays(31))
        w.service.redeem("boris", "RUB", "req-00001")
        w.refund("r1", "anna-1", 29900, full = true)
        assertEquals(ReferralBalance("RUB", -2000, 0), w.balance("boris"))
    }

    @Test
    fun `an invitee who deleted the account still has the share reversed by a refund`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        w.referrals.deleteUser("anna", w.now)
        w.refund("r1", "anna-1", 29900, full = true)
        assertEquals(ReferralBalance("RUB", 0, 0), w.balance("boris"))
    }

    @Test
    fun `a referrer deleted before the invitee pays earns nothing`() = runBlocking {
        val code = w.borisInvitesAnna()
        w.referrals.deleteUser("boris", w.now)
        w.pay("anna", "anna-1")
        assertEquals(emptyList<Any>(), w.referrals.entries("boris"))
        assertEquals(false, w.service.summary("anna").referred)
        assertEquals(AttachResult.UNKNOWN_CODE, w.service.attach("gleb", code))
    }

    @Test
    fun `a share of zero percent turns rewards off`() = runBlocking {
        val disabled = ReferralRewards(w.referrals, w.entitlementRepo, w.policy.copy(sharePercent = 0)) { w.now }
        w.borisInvitesAnna()
        w.entitlementRepo.recordPaymentAndExtend("anna-1", "anna", PaymentSource.YOOKASSA, 29900, "RUB", "{}") {
            GrantWindow(w.now, w.now.plus(w.month))
        }
        disabled.onPayment(VerifiedPayment("anna-1", "anna", PaymentSource.YOOKASSA, 29900, 0, "RUB"))
        assertEquals(null, w.balance("boris"))
    }

    @Test
    fun `a partial refund seen by the payment step is not taken back twice by its notification`() = runBlocking {
        w.borisInvitesAnna()
        // The payment is processed late: YooKassa already reports 100 RUB refunded.
        w.pay("anna", "anna-1", refunded = 10000)
        assertEquals(ReferralBalance("RUB", 0, 3980), w.balance("boris"))
        // Then that same refund's own notification arrives.
        w.refund("r1", "anna-1", 10000, full = false)
        assertEquals(ReferralBalance("RUB", 0, 3980), w.balance("boris"), "already accounted for")
    }

    @Test
    fun `a refund recorded while the reward step runs is settled by that step`() = runBlocking {
        w.borisInvitesAnna()
        // The refund lands between the reward step's refund check and its accrual insert: the
        // refund notification found no accrual to take back.
        val racing = object : ReferralRepository by w.referrals {
            override suspend fun appendIfAbsent(entry: LedgerEntry): Boolean {
                // The refund is recorded (and its notification handled) before the accrual exists.
                w.entitlements.applyRefund(VerifiedRefund("r1", "anna-1", 29900, "RUB", full = true, rawPayload = "{}"))
                return w.referrals.appendIfAbsent(entry)
            }
        }
        val rewards = ReferralRewards(racing, w.entitlementRepo, w.policy) { w.now }
        w.entitlementRepo.recordPaymentAndExtend("anna-1", "anna", PaymentSource.YOOKASSA, 29900, "RUB", "{}") {
            GrantWindow(w.now, w.now.plus(w.month))
        }
        rewards.onPayment(VerifiedPayment("anna-1", "anna", PaymentSource.YOOKASSA, 29900, 0, "RUB"))
        assertEquals(ReferralBalance("RUB", 0, 0), w.balance("boris"))
    }

    @Test
    fun `partial refunds settle to the same balance in any order, without a full flag`() = runBlocking {
        val shareAfter = { refunded: Long -> (29900 - refunded) * 20 / 100 }
        for (order in listOf(listOf(5000L, 12000L, 12900L), listOf(12900L, 12000L, 5000L))) {
            val world = ReferralWorld()
            world.borisInvitesAnna()
            world.pay("anna", "anna-1")
            var refunded = 0L
            order.forEachIndexed { i, amount ->
                refunded += amount
                world.refund("r$i", "anna-1", amount, full = false)
                assertEquals(ReferralBalance("RUB", 0, shareAfter(refunded)), world.balance("boris"), "order $order step $i")
            }
            assertEquals(ReferralBalance("RUB", 0, 0), world.balance("boris"))
        }
    }

    @Test
    fun `a refund recorded before the payment leaves only what was kept, and the repurchase earns nothing`() = runBlocking {
        w.borisInvitesAnna()
        w.refund("r1", "anna-1", 29900, full = true) // the refund notification overtook the payment
        w.pay("anna", "anna-1")
        w.pay("anna", "anna-2")
        assertEquals(null, w.balance("boris"))

        val other = ReferralWorld()
        other.borisInvitesAnna()
        other.refund("r1", "anna-1", 10000, full = false)
        other.pay("anna", "anna-1")
        assertEquals(ReferralBalance("RUB", 0, 3980), other.balance("boris"))
    }

    @Test
    fun `refunds larger than the payment never push the share below zero`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        w.refund("r1", "anna-1", 99999, full = false)
        w.refund("r2", "anna-1", 99999, full = false)
        assertEquals(ReferralBalance("RUB", 0, 0), w.balance("boris"))

        val other = ReferralWorld()
        other.borisInvitesAnna()
        other.pay("anna", "anna-1", refunded = 50000)
        assertEquals(null, other.balance("boris"))
    }

    @Test
    fun `refunds settle with the percent the share was earned at, not today's policy`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1") // earned at 20%: 5980
        // The owner later changes the percent; a new revision settles this old accrual.
        val tenPercent = ReferralRewards(w.referrals, w.entitlementRepo, w.policy.copy(sharePercent = 10)) { w.now }
        w.entitlementRepo.recordRefund(VerifiedRefund("r1", "anna-1", 100, "RUB", false, "{}"), w.now)
        tenPercent.onRefund(VerifiedRefund("r1", "anna-1", 100, "RUB", false, "{}"))
        assertEquals(ReferralBalance("RUB", 0, 5960), w.balance("boris"), "20% of the 29800 kept, not 10%")
    }

    @Test
    fun `a refund only the provider reported still counts when the next refund settles`() = runBlocking {
        w.borisInvitesAnna()
        // YooKassa already reports 100 RUB refunded, but that refund's notification never arrives.
        w.pay("anna", "anna-1", refunded = 10000)
        assertEquals(ReferralBalance("RUB", 0, 3980), w.balance("boris"))
        // A second refund of 50 RUB: YooKassa reports 150 RUB refunded in total.
        w.refund("r2", "anna-1", 5000, full = false, providerTotal = 15000)
        assertEquals(ReferralBalance("RUB", 0, 2980), w.balance("boris"))
    }

    @Test
    fun `an accrual without a base keeps its share on partial refunds and loses it on a full one`() = runBlocking {
        w.borisInvitesAnna()
        w.entitlementRepo.recordPaymentAndExtend("anna-1", "anna", PaymentSource.YOOKASSA, 29900, "RUB", "{}") {
            GrantWindow(w.now, w.now.plus(w.month))
        }
        w.referrals.appendIfAbsent(LedgerEntry("boris", LedgerKind.ACCRUAL, "accrual:anna", "anna-1", "anna", "RUB", 5980,
            availableAt = w.now.plus(Duration.ofDays(30)), periodDays = null, completed = false, createdAt = w.now))
        w.refund("r1", "anna-1", 10000, full = false)
        assertEquals(ReferralBalance("RUB", 0, 5980), w.balance("boris"), "unknown base: never guess")
        w.refund("r2", "anna-1", 19900, full = true)
        assertEquals(ReferralBalance("RUB", 0, 0), w.balance("boris"))
    }
}

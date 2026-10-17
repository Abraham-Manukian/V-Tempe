package com.vtempe.server

import com.vtempe.server.features.account.data.service.AccountDeletionService
import com.vtempe.server.features.referral.domain.model.AttachResult
import com.vtempe.server.features.referral.domain.model.DebitResult
import com.vtempe.server.features.referral.domain.model.IssueCodeResult
import com.vtempe.server.features.referral.domain.model.RedeemResult
import com.vtempe.server.features.referral.domain.model.ReferralBalance
import com.vtempe.server.features.sync.data.repo.InMemorySyncBlobRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/** Codes, entering a code, spending the balance, and account deletion. */
class ReferralServiceTest {
    private val w = ReferralWorld()
    private val day31 = Duration.ofDays(31)

    /** anna's first payment earned boris 5980; the hold is over. */
    private suspend fun borisHasEarned() {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        w.advance(day31)
    }

    @Test
    fun `only a subscriber gets a code, and always the same one`() = runBlocking {
        assertEquals(IssueCodeResult.SubscriptionRequired, w.service.issueCode("boris"))
        w.pay("boris", "boris-1")
        assertEquals(IssueCodeResult.Issued("BRSKCDM2"), w.service.issueCode("boris"))
        assertEquals(IssueCodeResult.Issued("BRSKCDM2"), w.service.issueCode("boris"))
        w.advance(Duration.ofDays(30)) // expired, but within the 3-day grace
        assertEquals(IssueCodeResult.Issued("BRSKCDM2"), w.service.issueCode("boris"))
        w.advance(Duration.ofDays(3))
        assertEquals(IssueCodeResult.SubscriptionRequired, w.service.issueCode("boris"))
    }

    @Test
    fun `a code taken by someone else is never handed out twice, and allocation gives up`() = runBlocking {
        w.pay("boris", "b"); w.pay("vera", "v"); w.pay("gleb", "g")
        w.nextCode = { "BRSKCDM2" }
        w.codeOf("boris")
        assertThrows(IllegalStateException::class.java) { runBlocking { w.service.issueCode("vera") } }
        assertEquals(null, w.referrals.codeOf("vera"))
        var first = true
        w.nextCode = { if (first) "BRSKCDM2".also { first = false } else "VERAAAA3" }
        assertEquals(IssueCodeResult.Issued("VERAAAA3"), w.service.issueCode("vera"))
    }

    @Test
    fun `entering a code follows the rules`() = runBlocking {
        val code = w.borisInvitesAnna()
        assertEquals(AttachResult.INVALID_CODE, w.service.attach("gleb", "abc"))
        assertEquals(AttachResult.INVALID_CODE, w.service.attach("gleb", "BRSKCDM0")) // 0 is not in the alphabet
        assertEquals(AttachResult.UNKNOWN_CODE, w.service.attach("gleb", "ZZZZZZZ2"))
        assertEquals(AttachResult.SELF_REFERRAL, w.service.attach("boris", code))
        assertEquals(AttachResult.ATTACHED, w.service.attach("anna", " brsk-cdm2 "), "same code again is a safe retry")

        w.pay("vera", "v")
        assertEquals(AttachResult.ALREADY_ATTACHED, w.service.attach("anna", w.codeOf("vera")), "the first referrer is final")
        w.pay("dima", "d")
        assertEquals(AttachResult.ALREADY_PAID, w.service.attach("dima", code), "only new customers count")

        w.advance(Duration.ofDays(33)) // boris's subscription and grace are over
        assertEquals(AttachResult.CODE_INACTIVE, w.service.attach("gleb", code))
    }

    @Test
    fun `two codes entered at once leave exactly one referrer`() = runBlocking {
        w.pay("boris", "b"); w.pay("vera", "v")
        val codes = listOf(w.codeOf("boris"), w.codeOf("vera"))
        val results = (0 until 8).map { i -> async(Dispatchers.Default) { w.service.attach("anna", codes[i % 2]) } }.awaitAll()
        val referrer = w.referrals.attributionOf("anna")!!.referrerId
        val winningCode = codes[if (referrer == "boris") 0 else 1]
        results.forEachIndexed { i, r ->
            assertEquals(if (codes[i % 2] == winningCode) AttachResult.ATTACHED else AttachResult.ALREADY_ATTACHED, r)
        }
    }

    @Test
    fun `spending the balance buys access once per request`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1")
        assertEquals(RedeemResult.Insufficient(0, 2000), w.service.redeem("boris", "RUB", "req-00001"), "still on hold")
        w.advance(day31)
        // Day 31 is inside the 3-day grace, so the bought week continues the old expiry.
        val expected = w.entitlements.current("boris").expiresAt!!.plus(Duration.ofDays(7))

        assertEquals(RedeemResult.Redeemed(expected), w.service.redeem("boris", "rub", "req-00001"))
        assertEquals(RedeemResult.Redeemed(expected), w.service.redeem("boris", "RUB", "req-00001"), "retry: no second charge")
        assertEquals(ReferralBalance("RUB", 3980, 0), w.balance("boris"))
        assertEquals(RedeemResult.RequestReused, w.service.redeem("boris", "USD", "req-00001"))
        assertEquals(RedeemResult.NoOffer, w.service.redeem("boris", "EUR", "req-00003"))
        assertEquals(RedeemResult.InvalidRequest, w.service.redeem("boris", "RUB", "short"))
        assertEquals(RedeemResult.InvalidRequest, w.service.redeem("boris", "RUB", "bad id with spaces"))
    }

    @Test
    fun `a balance equal to the price is enough, one kopeck less is not`() = runBlocking {
        w.borisInvitesAnna()
        w.pay("anna", "anna-1", amount = 10000) // share 2000
        w.advance(day31)
        assertTrue(w.service.redeem("boris", "RUB", "req-00001") is RedeemResult.Redeemed)
        assertEquals(ReferralBalance("RUB", 0, 0), w.balance("boris"))

        val other = ReferralWorld()
        other.borisInvitesAnna()
        other.pay("anna", "anna-1", amount = 9995) // share 1999
        other.advance(day31)
        assertEquals(RedeemResult.Insufficient(1999, 2000), other.service.redeem("boris", "RUB", "req-00001"))
    }

    @Test
    fun `a fully expired subscriber's bought week starts now`() = runBlocking {
        borisHasEarned()
        w.advance(Duration.ofDays(10)) // well past the grace
        assertEquals(RedeemResult.Redeemed(w.now.plus(Duration.ofDays(7))), w.service.redeem("boris", "RUB", "req-00001"))
    }

    @Test
    fun `parallel redemptions never spend more than the balance`() = runBlocking {
        borisHasEarned() // 5980: enough for two 2000 redemptions, not three
        val before = w.entitlements.current("boris").expiresAt!!

        val results = (1..6).map { i -> async(Dispatchers.Default) { w.service.redeem("boris", "RUB", "parallel-$i") } }.awaitAll()

        assertEquals(2, results.count { it is RedeemResult.Redeemed }, "results: $results")
        assertEquals(ReferralBalance("RUB", 1980, 0), w.balance("boris"))
        assertEquals(before.plus(Duration.ofDays(14)), w.entitlements.current("boris").expiresAt)
    }

    @Test
    fun `a redemption made with an earlier clock still sees the later one`() = runBlocking {
        borisHasEarned() // 5980 available
        assertTrue(w.referrals.debit("boris", "redeem:boris:late", "RUB", 4000, 7, w.now) is DebitResult.Debited)
        // A second request whose clock reads an hour earlier (another instance, or it read the time
        // before waiting for the lock) must not skip the debit above.
        val earlier = w.now.minus(Duration.ofHours(1))
        assertEquals(DebitResult.Insufficient(1980), w.referrals.debit("boris", "redeem:boris:early", "RUB", 4000, 7, earlier))
    }

    @Test
    fun `a redemption that crashed before granting access is finished by the next redeem`() = runBlocking {
        borisHasEarned()
        val before = w.entitlements.current("boris").expiresAt!!
        w.entitlementRepo.failNextPayment = true
        assertThrows(IllegalStateException::class.java) { runBlocking { w.service.redeem("boris", "RUB", "req-crash-1") } }
        assertEquals(before, w.entitlements.current("boris").expiresAt, "debited, not granted yet")
        w.service.summary("boris")
        assertEquals(before, w.entitlements.current("boris").expiresAt, "summary writes nothing")

        assertTrue(w.service.redeem("boris", "RUB", "req-crash-1") is RedeemResult.Redeemed)
        assertEquals(before.plus(Duration.ofDays(7)), w.entitlements.current("boris").expiresAt)
        assertEquals(ReferralBalance("RUB", 3980, 0), w.balance("boris"), "charged once")
        assertTrue(w.referrals.entries("boris").single { it.sourceRef == "redeem:boris:req-crash-1" }.completed)
    }

    @Test
    fun `a deleted account can neither spend its retained balance nor enter a code`() = runBlocking {
        borisHasEarned()
        w.pay("vera", "v")
        val veras = w.codeOf("vera")
        w.entitlementRepo.failNextPayment = true
        assertThrows(IllegalStateException::class.java) { runBlocking { w.service.redeem("boris", "RUB", "req-before-delete") } }
        w.referrals.deleteUser("boris", w.now)
        val expiry = w.entitlements.current("boris").expiresAt

        assertEquals(RedeemResult.AccountClosed, w.service.redeem("boris", "RUB", "req-after-delete"))
        assertEquals(RedeemResult.AccountClosed, w.service.redeem("boris", "RUB", "req-before-delete"), "no resurrection by retry")
        assertEquals(expiry, w.entitlements.current("boris").expiresAt)
        assertEquals(AttachResult.ACCOUNT_CLOSED, w.service.attach("boris", veras))
        assertEquals(null, w.referrals.codeOf("boris"))
        assertEquals(0, w.service.summary("boris").invitedCount)
    }

    @Test
    fun `a redemption racing an account deletion never leaves the deleted account with access`() = runBlocking {
        borisHasEarned()
        val deletion = AccountDeletionService(InMemorySyncBlobRepository(), w.entitlementRepo, w.referrals) { w.now }
        // The deletion runs right after the debit, before the access grant.
        w.entitlementRepo.beforeNextPayment = { deletion.deleteAccountData("boris") }

        w.service.redeem("boris", "RUB", "req-race-01")

        assertEquals(null, w.entitlements.current("boris").expiresAt, "no paid access survives the deletion")
        assertEquals(IssueCodeResult.SubscriptionRequired, w.service.issueCode("boris"))
    }

    @Test
    fun `nobody can enter the code of a closed account`() = runBlocking {
        val code = w.borisInvitesAnna()
        w.referrals.saveCode("boris", code, w.now)
        w.referrals.deleteUser("boris", w.now)
        w.referrals.saveCode("boris", code, w.now) // even if a code row came back somehow
        assertEquals(AttachResult.CODE_INACTIVE, w.service.attach("gleb", code))
    }
}

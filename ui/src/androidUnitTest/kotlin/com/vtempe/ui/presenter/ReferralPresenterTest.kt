package com.vtempe.ui.presenter

import com.vtempe.shared.domain.referral.AttachCodeResult
import com.vtempe.shared.domain.referral.ReferralCodeResult
import com.vtempe.shared.domain.referral.ReferralOverview
import com.vtempe.shared.domain.referral.ReferralRepository
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.ui.util.formatMoney
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The invite screen's state: what it shows for each server answer. */
class ReferralPresenterTest {
    private val base = ReferralOverview(
        code = null, canInvite = true, referred = false, invitedCount = 0, rewardedCount = 0,
        balances = emptyList(), spendingAvailable = false, sharePercent = 20, holdDays = 30,
    )

    private class FakeReferrals(var overview: DataResult<ReferralOverview>) : ReferralRepository {
        var code: ReferralCodeResult = ReferralCodeResult.Issued("BRSKCDM2")
        var attachResult = AttachCodeResult.ATTACHED
        val attached = mutableListOf<String>()
        var overviewCalls = 0
        override suspend fun overview(): DataResult<ReferralOverview> = overview.also { overviewCalls++ }
        override suspend fun requestCode(): ReferralCodeResult = code
        override suspend fun attach(code: String): AttachCodeResult = attachResult.also { attached += code }
    }

    private fun TestScope.presenter(repo: FakeReferrals) = ReferralPresenterDelegate(repo, this)

    @Test
    fun loadsTheOverview() = runTest {
        val p = presenter(FakeReferrals(DataResult.Success(base)))
        p.refresh(); advanceUntilIdle()
        assertEquals(base, p.state.value.overview)
        assertFalse(p.state.value.loading)
    }

    @Test
    fun aSignedOutUserIsAskedToSignInNotShownAnError() = runTest {
        val p = presenter(FakeReferrals(DataResult.Failure(DataResult.Reason.Http, code = 401)))
        p.refresh(); advanceUntilIdle()
        assertTrue(p.state.value.signInRequired)
        assertFalse(p.state.value.loadFailed)
    }

    @Test
    fun aNetworkFailureCanBeRetried() = runTest {
        val repo = FakeReferrals(DataResult.Failure(DataResult.Reason.Network))
        val p = presenter(repo)
        p.refresh(); advanceUntilIdle()
        assertTrue(p.state.value.loadFailed)
        repo.overview = DataResult.Success(base)
        p.refresh(); advanceUntilIdle()
        assertFalse(p.state.value.loadFailed)
        assertEquals(base, p.state.value.overview)
    }

    @Test
    fun anIssuedCodeAppearsWithoutReloading() = runTest {
        val p = presenter(FakeReferrals(DataResult.Success(base)))
        p.refresh(); advanceUntilIdle()
        p.requestCode(); advanceUntilIdle()
        assertEquals("BRSKCDM2", p.state.value.overview?.code)
        assertFalse(p.state.value.requestingCode)
    }

    @Test
    fun aNonSubscriberIsToldWhyNoCodeCame() = runTest {
        val repo = FakeReferrals(DataResult.Success(base)).apply { code = ReferralCodeResult.SubscriptionRequired }
        val p = presenter(repo)
        p.refresh(); advanceUntilIdle()
        p.requestCode(); advanceUntilIdle()
        assertTrue(p.state.value.subscriptionRequired)
        assertNull(p.state.value.overview?.code)
    }

    @Test
    fun applyingACodeClearsTheInputAndReloads() = runTest {
        val repo = FakeReferrals(DataResult.Success(base))
        val p = presenter(repo)
        p.refresh(); advanceUntilIdle()
        p.onCodeInput(" brsk-cdm2 ")
        p.attach(); advanceUntilIdle()
        assertEquals(listOf("brsk-cdm2"), repo.attached, "trimmed, the server normalizes the rest")
        assertEquals(AttachCodeResult.ATTACHED, p.state.value.attachResult)
        assertEquals("", p.state.value.codeInput)
        assertEquals(2, repo.overviewCalls)
    }

    @Test
    fun aRejectedCodeKeepsTheInputAndSaysWhy() = runTest {
        val repo = FakeReferrals(DataResult.Success(base)).apply { attachResult = AttachCodeResult.UNKNOWN_CODE }
        val p = presenter(repo)
        p.onCodeInput("ZZZZZZZ2")
        p.attach(); advanceUntilIdle()
        assertEquals(AttachCodeResult.UNKNOWN_CODE, p.state.value.attachResult)
        assertEquals("ZZZZZZZ2", p.state.value.codeInput)
        p.onCodeInput("ZZZZZZZ3")
        assertNull(p.state.value.attachResult, "editing the code clears the old message")
    }

    @Test
    fun emptyInputIsNotSentAndLongInputIsCapped() = runTest {
        val repo = FakeReferrals(DataResult.Success(base))
        val p = presenter(repo)
        p.onCodeInput("   ")
        p.attach(); advanceUntilIdle()
        assertTrue(repo.attached.isEmpty())
        p.onCodeInput("X".repeat(100))
        assertEquals(16, p.state.value.codeInput.length)
    }

    @Test
    fun moneyIsShownInWholeUnitsWithTheCurrencySign() {
        assertEquals("-0,05 ₽", formatMoney(-5, "RUB"))
        assertEquals("$0.00", formatMoney(0, "USD"))
        assertEquals("59,80 ₽", formatMoney(5980, "RUB"))
        assertEquals("0,05 ₽", formatMoney(5, "RUB"))
        assertEquals("-20,00 ₽", formatMoney(-2000, "RUB"))
        assertEquals("$5.00", formatMoney(500, "USD"))
        assertEquals("12.34 EUR", formatMoney(1234, "eur"))
    }

    @Test
    fun aReloadAskedForDuringALoadIsNotDropped() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = object : ReferralRepository {
            var calls = 0
            override suspend fun overview(): DataResult<ReferralOverview> {
                calls++
                if (calls == 1) { gate.await(); return DataResult.Success(base) } // slow first load, from before the attach
                return DataResult.Success(base.copy(referred = true))
            }
            override suspend fun requestCode(): ReferralCodeResult = ReferralCodeResult.SubscriptionRequired
            override suspend fun attach(code: String) = AttachCodeResult.ATTACHED
        }
        val p = ReferralPresenterDelegate(repo, this)
        p.refresh(); advanceUntilIdle()
        p.onCodeInput("BRSKCDM2")
        p.attach(); advanceUntilIdle() // its reload is requested while the first load still runs
        gate.complete(Unit); advanceUntilIdle()
        assertEquals(true, p.state.value.overview?.referred, "the stale first answer must not win")
        assertEquals(2, repo.calls)
    }

    @Test
    fun signInStateStaysConsistent() = runTest {
        val repo = FakeReferrals(DataResult.Failure(DataResult.Reason.Http, code = 401))
        val p = presenter(repo)
        p.refresh(); advanceUntilIdle()
        repo.code = ReferralCodeResult.Failed(DataResult.Failure(DataResult.Reason.Network))
        p.requestCode(); advanceUntilIdle()
        assertTrue(p.state.value.signInRequired, "a network failure does not hide the sign-in need")

        repo.attachResult = AttachCodeResult.SIGN_IN_REQUIRED
        p.onCodeInput("BRSKCDM2"); p.attach(); advanceUntilIdle()
        repo.overview = DataResult.Success(base) // signed in now
        p.refresh(); advanceUntilIdle()
        assertFalse(p.state.value.signInRequired)
        assertNull(p.state.value.attachResult, "the old 'sign in' message is gone")
    }

    @Test
    fun aMessageAboutAnOlderStateGoesAwayOnReload() = runTest {
        val repo = FakeReferrals(DataResult.Success(base.copy(canInvite = false))).apply { code = ReferralCodeResult.SubscriptionRequired }
        val p = presenter(repo)
        p.refresh(); advanceUntilIdle()
        p.requestCode(); advanceUntilIdle()
        assertTrue(p.state.value.subscriptionRequired)
        repo.overview = DataResult.Success(base) // the user subscribed meanwhile
        p.refresh(); advanceUntilIdle()
        assertFalse(p.state.value.subscriptionRequired)
        assertFalse(p.state.value.codeRequestFailed)
    }
}

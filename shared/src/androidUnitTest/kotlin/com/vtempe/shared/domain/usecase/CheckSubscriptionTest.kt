package com.vtempe.shared.domain.usecase

import com.vtempe.shared.data.repo.LastKnownEntitlementStore
import com.vtempe.shared.data.sync.FakeAuthRepository
import com.vtempe.shared.data.sync.inMemorySettings
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.shared.domain.repository.SubscriptionEntitlement
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

/** VT-AUD-053: the paywall shows the server's entitlement, and offline only this account's last one. */
class CheckSubscriptionTest {

    private class FakeEntitlements : EntitlementRepository {
        var result: DataResult<SubscriptionEntitlement> = DataResult.Failure(DataResult.Reason.Network)
        var calls = 0
        override suspend fun fetchEntitlement(): DataResult<SubscriptionEntitlement> { calls++; return result }
    }

    private val auth = FakeAuthRepository()
    private val entitlements = FakeEntitlements()
    private val lastKnown = LastKnownEntitlementStore(inMemorySettings())
    private val check = CheckSubscription(auth, entitlements, lastKnown)

    @Test
    fun `signed out is reported without asking the server`() = runBlocking {
        assertEquals(SubscriptionStatus.SignedOut, check())
        assertEquals(0, entitlements.calls)
    }

    @Test
    fun `the server answer is shown fresh and remembered`() = runBlocking {
        auth.signInAs("alice")
        entitlements.result = DataResult.Success(SubscriptionEntitlement(true, "2026-12-01T00:00:00Z"))

        assertEquals(SubscriptionStatus.Known(true, "2026-12-01T00:00:00Z", stale = false), check())
        assertEquals(SubscriptionEntitlement(true, "2026-12-01T00:00:00Z"), lastKnown.load("alice"))
    }

    @Test
    fun `an inactive server answer is not overridden by a client guess`() = runBlocking {
        auth.signInAs("alice")
        entitlements.result = DataResult.Success(SubscriptionEntitlement(false, null))

        assertEquals(SubscriptionStatus.Known(false, null, stale = false), check())
    }

    @Test
    fun `offline the last known status is shown marked stale`() = runBlocking {
        auth.signInAs("alice")
        entitlements.result = DataResult.Success(SubscriptionEntitlement(true, null))
        check()
        entitlements.result = DataResult.Failure(DataResult.Reason.Network)

        assertEquals(SubscriptionStatus.Known(true, null, stale = true), check())
    }

    @Test
    fun `another account's last status is never shown`() = runBlocking {
        auth.signInAs("alice")
        entitlements.result = DataResult.Success(SubscriptionEntitlement(true, null))
        check()
        auth.signInAs("bob")
        entitlements.result = DataResult.Failure(DataResult.Reason.Http, code = 503)

        assertEquals(SubscriptionStatus.Unknown, check())
    }

    @Test
    fun `offline with nothing confirmed is unknown`() = runBlocking {
        auth.signInAs("alice")

        assertEquals(SubscriptionStatus.Unknown, check())
    }
}

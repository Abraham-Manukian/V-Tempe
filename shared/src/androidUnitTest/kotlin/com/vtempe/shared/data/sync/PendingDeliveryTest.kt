package com.vtempe.shared.data.sync

import com.vtempe.shared.data.repo.PendingSyncStore
import com.vtempe.shared.domain.account.AccountDataOutcome
import com.vtempe.shared.domain.account.DefaultAccountSession
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** VT-AUD-056: a change that could not be uploaded is delivered later without another edit. */
class PendingDeliveryTest {
    private val device = TestDevice()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        device.close()
    }

    private suspend fun signedIn(uid: String) {
        device.auth.signInAs(uid)
        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn(uid))
    }

    @Test
    fun anOfflineChangeIsDeliveredAfterARestartWithoutAnotherEdit() = runBlocking<Unit> {
        signedIn("anna")
        device.server.failRequests = true
        device.weight.logWeight("2026-10-12", 78.0)
        device.flushPushes()
        assertEquals(null, device.server.payload("anna", "weight"))

        // The app restarts: the pending mark was on disk; launch reconciles with the network back.
        assertTrue(PendingSyncStore(device.settings).isPending(SyncDomain.WEIGHT))
        device.server.failRequests = false
        DefaultAccountSession(device.coordinator, device.auth, device.owner, scope).reconcile()

        assertTrue(device.server.payload("anna", "weight").orEmpty().contains("78.0"))
        assertFalse(device.pendingSync.isPending(SyncDomain.WEIGHT))
    }

    @Test
    fun aChangeMadeDuringAnUploadIsNotLost() = runBlocking<Unit> {
        signedIn("anna")
        val gate = CompletableDeferred<Unit>()
        device.server.putGate = gate
        device.weight.logWeight("2026-10-11", 80.0)
        val upload = async(Dispatchers.Default) { device.flushPushes() }
        withTimeout(5_000) { device.server.putReceived.await() }

        device.weight.logWeight("2026-10-12", 79.0) // while the first upload is in flight
        gate.complete(Unit)
        withTimeout(5_000) { upload.await() }
        assertTrue(device.pendingSync.isPending(SyncDomain.WEIGHT), "the second change is still owed")

        device.sync.pushPending()
        assertTrue(device.server.payload("anna", "weight").orEmpty().contains("79.0"))
        assertFalse(device.pendingSync.isPending(SyncDomain.WEIGHT))
    }

    @Test
    fun aGuestsMarksNeverReachAnotherAccount() = runBlocking<Unit> {
        signedIn("anna")
        device.server.failRequests = true
        device.weight.logWeight("2026-10-12", 78.0)
        device.flushPushes()
        assertTrue(device.pendingSync.isPending(SyncDomain.WEIGHT))

        device.coordinator.signOut(force = true) // device data of anna is removed
        assertTrue(device.pendingSync.pendingDomains().isEmpty())
    }
}

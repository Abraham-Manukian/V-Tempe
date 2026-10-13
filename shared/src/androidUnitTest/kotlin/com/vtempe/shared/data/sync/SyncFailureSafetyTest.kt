package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataFailure
import com.vtempe.shared.domain.account.AccountDataOutcome
import com.vtempe.shared.domain.account.AccountDeletionOutcome
import com.vtempe.shared.domain.account.GuestDataChoice
import com.vtempe.shared.domain.account.SignOutOutcome
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VT-AUD-058 (read failure is not deletion), 059 (no upload after the wipe), 060 (no Ready on a corrupt account). */
class SyncFailureSafetyTest {
    private val device = TestDevice()

    @AfterTest
    fun tearDown() = device.close()

    private suspend fun signedInWithData(uid: String) {
        device.auth.signInAs(uid)
        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn(uid))
        device.recordData(uid)
    }

    @Test
    fun unreadableLocalProfileIsNotUploadedAsDeletedAndBlocksSilentSignOut() = runBlocking<Unit> {
        signedInWithData("anna")
        val cloudProfile = device.server.payload("anna", "profile")
        assertTrue(cloudProfile.orEmpty().contains("anna-profile"))

        device.corruptStoredProfile()

        assertFalse(device.sync.pushAll("anna"))
        assertEquals(cloudProfile, device.server.payload("anna", "profile"))
        assertEquals(SignOutOutcome.Unsynced, device.coordinator.signOut())
        assertEquals("anna", device.owner.ownerUid(), "device data is kept for the user to decide")

        device.sync.pushDomain(SyncDomain.PROFILE)
        assertEquals(cloudProfile, device.server.payload("anna", "profile"))
    }

    @Test
    fun aGenuinelyMissingProfileIsStillUploadedAsAbsent() = runBlocking<Unit> {
        signedInWithData("anna")
        device.profiles.clearAll()
        assertTrue(device.sync.pushAll("anna"))
        assertEquals("{}", device.server.payload("anna", "profile"))
    }

    @Test
    fun corruptAccountSnapshotNeverBecomesReadyAndRestoresNothing() = runBlocking<Unit> {
        device.server.seed("anna", "profile", "not-json")
        device.server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        device.auth.signInAs("anna")

        assertEquals(AccountDataOutcome.Failed(AccountDataFailure.UNREADABLE_DATA), device.coordinator.onSignedIn("anna"))
        assertNull(device.owner.ownerUid())
        assertNull(device.weight.latestWeight(), "all or nothing: no half-restored account")

        device.server.seed("anna", "profile", "{}") // repaired on the server: the retry succeeds
        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn("anna"))
        assertEquals(70.0, device.weight.latestWeight())
    }

    @Test
    fun keepingACorruptAccountKeepsTheGuestData() = runBlocking<Unit> {
        device.server.seed("anna", "profile", "not-json")
        device.server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        device.recordData("guest")
        device.auth.signInAs("anna")
        assertEquals(AccountDataOutcome.NeedsGuestChoice, device.coordinator.onSignedIn("anna"))

        assertEquals(
            AccountDataOutcome.Failed(AccountDataFailure.UNREADABLE_DATA),
            device.coordinator.resolveGuestChoice("anna", GuestDataChoice.KEEP_ACCOUNT_DATA)
        )
        assertEquals("guest-profile", device.profiles.getProfile()?.id)
        assertNull(device.owner.ownerUid())
    }

    @Test
    fun anUploadAlreadyUnderWayCannotBringDataBackAfterDeletion() = runBlocking<Unit> {
        signedInWithData("anna")
        val gate = CompletableDeferred<Unit>()
        device.server.putGate = gate
        device.weight.logWeight("2026-10-01", 79.0)

        val upload = async(Dispatchers.Default) { device.flushPushes() }
        withTimeout(5_000) { device.server.putReceived.await() }
        val deletion = async(Dispatchers.Default) { device.coordinator.deleteAccount() }
        delay(200)
        assertTrue(device.server.blobs["anna"].orEmpty().isNotEmpty(), "deletion waits for the upload under way")

        gate.complete(Unit)
        withTimeout(5_000) { upload.await() }
        assertEquals(AccountDeletionOutcome.Deleted, withTimeout(5_000) { deletion.await() })
        assertTrue(device.server.blobs["anna"].isNullOrEmpty(), "server copy: ${device.server.blobs["anna"]}")
    }
}

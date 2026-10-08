package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataState
import com.vtempe.shared.domain.account.AccountDeletionOutcome
import com.vtempe.shared.domain.account.DefaultAccountSession
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.repository.ReauthCredential
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AccountDeletionTest {
    private val device = TestDevice()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val session = DefaultAccountSession(device.coordinator, device.auth, device.owner, scope)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        device.close()
    }

    @Test
    fun deletesServerDataThenAccountThenDeviceData() = runBlocking {
        signedInWithData("anna")
        val before = session.localDataGeneration.value

        assertEquals(AccountDeletionOutcome.Deleted, session.deleteAccount())

        assertTrue(device.server.blobs["anna"].isNullOrEmpty())
        assertEquals(listOf("anna"), device.auth.deletedUids)
        assertNull(device.auth.authState.value)
        device.assertNoLocalData()
        assertNull(device.owner.ownerUid())
        assertFalse(device.owner.isFullPushPending())
        assertEquals(before + 1, session.localDataGeneration.value)
    }

    @Test
    fun serverFailureKeepsTheAccountAndEverythingElse() = runBlocking {
        signedInWithData("anna")
        device.server.failRequests = true

        assertEquals(AccountDeletionOutcome.Failed, session.deleteAccount())

        assertTrue(device.auth.deletedUids.isEmpty())
        assertEquals("anna", device.auth.authState.value?.uid)
        assertEquals("anna", device.owner.ownerUid())
        assertTrue(device.workouts.current().containsKey("anna-workout"))
    }

    @Test
    fun staleSignInAsksForReauthenticationAndRetrySucceeds() = runBlocking {
        signedInWithData("anna")
        device.auth.recentLogin = false

        assertEquals(AccountDeletionOutcome.NeedsReauthentication, session.deleteAccount())
        // The account survives: its device copy stays and is queued to restore the erased server copy.
        assertEquals("anna", device.auth.authState.value?.uid)
        assertEquals("anna", device.owner.ownerUid())
        assertTrue(device.owner.isFullPushPending())
        assertTrue(device.workouts.current().containsKey("anna-workout"))

        device.auth.reauthenticate(ReauthCredential.Google("fresh-google-token"))
        assertEquals(AccountDeletionOutcome.Deleted, session.deleteAccount())
        assertTrue(device.server.blobs["anna"].isNullOrEmpty())
        device.assertNoLocalData()
        assertFalse(device.owner.isFullPushPending())
    }

    @Test
    fun abandonedDeletionRestoresServerCopyOnNextSettle() = runBlocking {
        signedInWithData("anna")
        device.auth.failDeletion = true

        assertEquals(AccountDeletionOutcome.Failed, session.deleteAccount())
        assertTrue(device.server.blobs["anna"].isNullOrEmpty())

        session.reconcile()
        assertTrue(device.server.payload("anna", "workoutProgress").orEmpty().contains("anna-workout"))
        assertFalse(device.owner.isFullPushPending())
    }

    @Test
    fun syncResumesForTheSurvivingAccountAfterAFailedDeletion() = runBlocking {
        signedInWithData("anna")
        device.auth.recentLogin = false
        device.server.failRequests = true

        session.deleteAccount()
        device.server.failRequests = false
        device.workouts.save(WorkoutProgress(workoutId = "anna-workout-2"))
        device.flushPushes()

        assertTrue(device.server.payload("anna", "workoutProgress").orEmpty().contains("anna-workout-2"))
    }

    @Test
    fun signedOutUserCannotDeleteAnything() = runBlocking {
        assertEquals(AccountDeletionOutcome.Failed, session.deleteAccount())
        assertTrue(device.auth.deletedUids.isEmpty())
    }

    private suspend fun signedInWithData(uid: String) {
        device.auth.signInAs(uid)
        session.onSignedIn(uid)
        withTimeout(5_000) { session.state.first { it != AccountDataState.Syncing } }
        device.recordData(uid)
        assertTrue(device.server.payload(uid, "workoutProgress").orEmpty().contains("$uid-workout"))
    }
}

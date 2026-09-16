package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataState
import com.vtempe.shared.domain.account.DefaultAccountSession
import com.vtempe.shared.domain.account.GuestDataChoice
import com.vtempe.shared.domain.model.WorkoutProgress
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
import kotlin.test.assertTrue

class AccountSessionTest {
    private val device = TestDevice()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val session = DefaultAccountSession(device.coordinator, device.auth, device.owner, scope)

    @AfterTest
    fun tearDown() {
        scope.cancel()
        device.close()
    }

    @Test
    fun installsFromBeforeOwnerTrackingKeepSignedInUsersData() = runBlocking {
        device.recordData("anna")
        device.auth.signInAs("anna")

        session.reconcile()

        assertEquals("anna", device.owner.ownerUid())
        assertTrue(device.workouts.current().containsKey("anna-workout"))
        assertEquals(0, session.localDataGeneration.value)
        device.workouts.save(WorkoutProgress(workoutId = "anna-workout-2"))
        device.flushPushes()
        assertTrue(device.server.payload("anna", "workoutProgress").orEmpty().contains("anna-workout-2"))
    }

    @Test
    fun launchAfterInterruptedSwitchRemovesPreviousAccountsData() = runBlocking {
        device.owner.startTracking()
        device.recordData("anna")
        device.owner.setOwnerUid("anna")
        device.auth.signInAs("boris")

        session.reconcile()

        device.assertNoLocalData()
        assertEquals("boris", device.owner.ownerUid())
        assertEquals(AccountDataState.Idle, session.state.value)
        assertEquals(1, session.localDataGeneration.value)
    }

    @Test
    fun guestChoiceIsExposedAndResolved() = runBlocking {
        device.server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        device.recordData("guest")
        device.auth.signInAs("anna")

        session.onSignedIn("anna")
        assertEquals(AccountDataState.NeedsGuestChoice("anna"), settled())

        session.resolveGuestChoice(GuestDataChoice.KEEP_ACCOUNT_DATA)
        assertEquals(AccountDataState.Idle, settled())
        assertEquals(70.0, device.weight.latestWeight())
        assertEquals(1, session.localDataGeneration.value)
    }

    @Test
    fun unsyncedSignOutWaitsForConfirmation() = runBlocking {
        device.auth.signInAs("anna")
        session.onSignedIn("anna")
        settled()
        device.recordData("anna")
        device.server.failRequests = true

        session.signOut()
        assertEquals(AccountDataState.SignOutUnsynced, settled())
        session.dismissSignOutWarning()
        assertEquals(AccountDataState.Idle, session.state.value)
        assertEquals("anna", device.auth.authState.value?.uid)

        session.signOut(force = true)
        assertEquals(AccountDataState.Idle, settled())
        device.assertNoLocalData()
    }

    @Test
    fun resetRebuildsScreensEvenWhenOwnerStaysTheSame() = runBlocking {
        device.auth.signInAs("anna")
        session.onSignedIn("anna")
        settled()
        device.recordData("anna")
        val before = session.localDataGeneration.value

        session.resetUserData()

        device.assertNoLocalData()
        assertEquals(before + 1, session.localDataGeneration.value)
        assertEquals(AccountDataState.Idle, session.state.value)
    }

    private suspend fun settled(): AccountDataState = withTimeout(5_000) {
        session.state.first { it != AccountDataState.Syncing }
    }
}

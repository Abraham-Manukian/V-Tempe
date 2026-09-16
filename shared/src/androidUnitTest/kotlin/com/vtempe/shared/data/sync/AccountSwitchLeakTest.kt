package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataOutcome
import com.vtempe.shared.domain.account.GuestDataChoice
import com.vtempe.shared.domain.account.SignOutOutcome
import com.vtempe.shared.domain.model.WorkoutProgress
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One device, several accounts, a server that files data under whoever the bearer token
 * belongs to. Real stores, real SQLite, real NetworkSyncRepository — only Firebase and the
 * HTTP transport are faked.
 */
class AccountSwitchLeakTest {
    private val device = TestDevice()
    private val server = device.server
    private val auth = device.auth
    private val owner = device.owner
    private val profiles = device.profiles
    private val workouts = device.workouts
    private val sleep = device.sleep
    private val weight = device.weight
    private val chat = device.chat
    private val coordinator = device.coordinator

    @AfterTest
    fun tearDown() = device.close()

    @Test
    fun switchingToNewAccountLeavesNothingFromPreviousAccount() = runBlocking {
        signIn("anna")
        recordAnnaData()
        assertEquals(SignOutOutcome.SignedOut, coordinator.signOut())

        assertEquals(AccountDataOutcome.Ready, signIn("boris"))
        assertNoLocalData()

        sleep.logSleep("2026-09-16", 420)
        workouts.save(WorkoutProgress(workoutId = "boris-workout"))
        flushPushes()
        assertFalse(server.payload("boris", "workoutProgress").orEmpty().contains("anna"))
        assertNull(server.payload("boris", "weight"))
        assertTrue(server.payload("anna", "workoutProgress").orEmpty().contains("anna-workout"))
    }

    @Test
    fun switchingToExistingAccountRestoresOnlyThatAccountsData() = runBlocking {
        server.seed("boris", "workoutProgress", """{"boris-workout":{"workoutId":"boris-workout"}}""")
        signIn("anna")
        recordAnnaData()
        coordinator.signOut()

        assertEquals(AccountDataOutcome.Ready, signIn("boris"))
        assertEquals(setOf("boris-workout"), workouts.current().keys)
        assertNull(weight.latestWeight(), "anna's weight survived because boris has no weight blob")
        assertNull(profiles.getProfile())
        assertTrue(chat.load().isEmpty())
    }

    @Test
    fun sameAccountGetsItsDataBackAfterSigningOutAndIn() = runBlocking {
        signIn("anna")
        recordAnnaData()
        coordinator.signOut()
        assertNoLocalData()

        signIn("anna")
        assertTrue(workouts.current().containsKey("anna-workout"))
        assertEquals(81.5, weight.latestWeight())
        assertEquals("anna-profile", profiles.getProfile()?.id)
    }

    @Test
    fun guestProgressMovesIntoEmptyAccount() = runBlocking {
        recordGuestData()
        assertEquals(AccountDataOutcome.Ready, signIn("anna"))

        assertTrue(workouts.current().containsKey("guest-workout"))
        assertTrue(server.payload("anna", "workoutProgress").orEmpty().contains("guest-workout"))
        assertEquals("guest-profile", profiles.getProfile()?.id)
        assertTrue(server.payload("anna", "profile").orEmpty().contains("guest-profile"))
    }

    @Test
    fun guestSigningIntoAccountWithDataMustChooseAndNothingSyncsMeanwhile() = runBlocking {
        server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        recordGuestData()

        assertEquals(AccountDataOutcome.NeedsGuestChoice, signIn("anna"))
        weight.logWeight("2026-09-16", 99.0)
        flushPushes()
        assertEquals("""{"2026-09-01":70.0}""", server.payload("anna", "weight"))
        assertTrue(workouts.current().containsKey("guest-workout"))
    }

    @Test
    fun guestChoosingAccountDataReplacesDeviceData() = runBlocking {
        server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        recordGuestData()
        signIn("anna")

        assertEquals(AccountDataOutcome.Ready, coordinator.resolveGuestChoice("anna", GuestDataChoice.KEEP_ACCOUNT_DATA))
        assertEquals(70.0, weight.latestWeight())
        assertTrue(workouts.current().isEmpty())
        assertNull(profiles.getProfile())
        assertTrue(chat.load().isEmpty())
    }

    @Test
    fun guestChoosingDeviceDataReplacesAccountData() = runBlocking {
        server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        recordGuestData()
        signIn("anna")

        assertEquals(AccountDataOutcome.Ready, coordinator.resolveGuestChoice("anna", GuestDataChoice.REPLACE_WITH_DEVICE_DATA))
        assertTrue(server.payload("anna", "workoutProgress").orEmpty().contains("guest-workout"))
        assertFalse(server.payload("anna", "weight").orEmpty().contains("70.0"), "account-only weight must not survive a replace")
        assertTrue(workouts.current().containsKey("guest-workout"))

        weight.logWeight("2026-09-16", 64.0)
        flushPushes()
        assertTrue(server.payload("anna", "weight").orEmpty().contains("64.0"))
    }

    @Test
    fun signOutWithUnsyncedDataAsksBeforeDeleting() = runBlocking {
        signIn("anna")
        recordAnnaData()
        server.failRequests = true

        assertEquals(SignOutOutcome.Unsynced, coordinator.signOut())
        assertEquals("anna", auth.authState.value?.uid)
        assertTrue(workouts.current().containsKey("anna-workout"))

        assertEquals(SignOutOutcome.SignedOut, coordinator.signOut(force = true))
        assertNull(auth.authState.value)
        assertNoLocalData()
    }

    @Test
    fun failedFetchWhenSwitchingAccountsStillHidesPreviousAccountsData() = runBlocking {
        signIn("anna")
        recordAnnaData()
        coordinator.signOut(force = true)
        // Simulate a device where the previous owner's data is still present (e.g. app killed mid-sign-out).
        owner.setOwnerUid("anna")
        recordAnnaData()

        server.failRequests = true
        auth.signInAs("boris")
        assertEquals(AccountDataOutcome.Failed, coordinator.onSignedIn("boris"))
        assertNoLocalData()

        server.failRequests = false
        workouts.save(WorkoutProgress(workoutId = "boris-workout"))
        flushPushes()
        assertNull(server.payload("boris", "workoutProgress"), "nothing may sync until the account data is settled")

        assertEquals(AccountDataOutcome.Ready, coordinator.onSignedIn("boris"))
    }

    @Test
    fun localChangesAfterSignOutAreNeverUploaded() = runBlocking {
        signIn("anna")
        recordAnnaData()
        coordinator.signOut()

        weight.logWeight("2026-09-16", 50.0)
        flushPushes()
        assertFalse(server.payload("anna", "weight").orEmpty().contains("50.0"))
    }

    private suspend fun signIn(uid: String): AccountDataOutcome {
        auth.signInAs(uid)
        return coordinator.onSignedIn(uid)
    }

    private suspend fun recordAnnaData() = device.recordData("anna")
    private suspend fun recordGuestData() = device.recordData("guest")
    private suspend fun assertNoLocalData() = device.assertNoLocalData()
    private suspend fun flushPushes() = device.flushPushes()
}

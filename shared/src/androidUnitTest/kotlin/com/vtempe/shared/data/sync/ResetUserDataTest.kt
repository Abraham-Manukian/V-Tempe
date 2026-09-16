package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataOutcome
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** "Регистрация заново": nothing of the previous progress may come back, on this device or from the account. */
class ResetUserDataTest {
    private val device = TestDevice()

    @AfterTest
    fun tearDown() = device.close()

    @Test
    fun resetClearsDeviceAndAccountProgressButKeepsSignIn() = runBlocking {
        signIn("anna")
        device.recordData("anna")
        device.sleep.logSleep("2026-09-15", 450, "anna sleep note")
        device.flushPushes()

        device.coordinator.resetUserData()

        device.assertNoLocalData()
        assertEquals("anna", device.auth.authState.value?.uid)
        assertEquals("anna", device.owner.ownerUid())
        for (domain in listOf("profile", "workoutProgress", "sleep", "sleepNotes", "weight")) {
            assertEquals("{}", device.server.payload("anna", domain), "$domain kept old progress in the account")
        }
        assertFalse(device.owner.isFullPushPending())
    }

    @Test
    fun oldProgressDoesNotReturnOnNextSignIn() = runBlocking {
        signIn("anna")
        device.recordData("anna")
        device.coordinator.resetUserData()
        device.coordinator.signOut()

        signIn("anna")
        assertNull(device.profiles.getProfile(), "old profile came back, so onboarding would be skipped")
        assertTrue(device.workouts.current().isEmpty())
        assertNull(device.weight.latestWeight())
    }

    @Test
    fun offlineResetStillClearsDeviceAndFinishesAccountWipeLater() = runBlocking {
        signIn("anna")
        device.recordData("anna")
        device.server.failRequests = true

        device.coordinator.resetUserData()
        device.assertNoLocalData()
        assertTrue(device.owner.isFullPushPending())
        assertTrue(device.server.payload("anna", "workoutProgress").orEmpty().contains("anna-workout"))

        device.server.failRequests = false
        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn("anna"))
        assertEquals("{}", device.server.payload("anna", "workoutProgress"))
        assertFalse(device.owner.isFullPushPending())
    }

    @Test
    fun guestResetClearsDeviceWithoutTouchingAnyAccount() = runBlocking {
        device.server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        device.recordData("guest")

        device.coordinator.resetUserData()

        device.assertNoLocalData()
        assertEquals("""{"2026-09-01":70.0}""", device.server.payload("anna", "weight"))
        assertNull(device.owner.ownerUid())
    }

    @Test
    fun accountWipedByResetCountsAsEmptyForGuestSignIn() = runBlocking {
        signIn("anna")
        device.recordData("anna")
        device.coordinator.resetUserData()
        device.coordinator.signOut()

        device.recordData("guest")
        device.auth.signInAs("anna")
        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn("anna"))
        assertTrue(device.server.payload("anna", "workoutProgress").orEmpty().contains("guest-workout"))
    }

    private suspend fun signIn(uid: String) {
        device.auth.signInAs(uid)
        device.coordinator.onSignedIn(uid)
    }
}

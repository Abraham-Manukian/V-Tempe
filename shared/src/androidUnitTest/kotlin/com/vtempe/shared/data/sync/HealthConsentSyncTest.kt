package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataOutcome
import com.vtempe.shared.domain.account.DefaultAccountSession
import com.vtempe.shared.domain.account.GuestDataChoice
import com.vtempe.shared.domain.account.SignOutOutcome
import com.vtempe.shared.domain.consent.HealthDataConsentStatus
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The server backup is a purpose of the health-data consent (and the server is abroad): without
 * the consent no user data leaves the device. Nothing is lost — it waits, and goes once given.
 */
class HealthConsentSyncTest {
    private val device = TestDevice()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val session = DefaultAccountSession(device.coordinator, device.auth, device.owner, scope)

    init {
        // As DI does: a grant delivers what waited.
        device.onConsentGranted = { runBlocking { session.reconcile() } }
    }

    @AfterTest
    fun tearDown() {
        scope.cancel()
        device.close()
    }

    /** Writes that carried any of [names]' data (empty "{}" erasures carry none). */
    private fun dataWrites(vararg names: String) =
        device.server.writes.filter { (_, _, body) -> names.any { "$it-" in body } || "81.5" in body }

    private suspend fun signedIn(uid: String) {
        device.auth.signInAs(uid)
        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn(uid))
    }

    @Test
    fun withoutConsentNoHealthDataReachesTheServer() = runBlocking {
        device.healthConsent = false
        signedIn("anna")
        assertEquals("anna", device.owner.ownerUid(), "the device data is anna's, so it would be uploaded")

        device.recordData("anna") // profile with health fields, workout log, weight

        assertEquals(emptyList(), dataWrites("anna"))
        assertTrue(device.pendingSync.pendingDomains().containsAll(listOf(SyncDomain.PROFILE, SyncDomain.WORKOUT_PROGRESS, SyncDomain.WEIGHT)))
    }

    @Test
    fun grantingConsentDeliversWhatWaited() = runBlocking {
        device.healthConsent = false
        signedIn("anna")
        device.recordData("anna")
        assertEquals(emptyList(), dataWrites("anna"), "held back before the grant")

        device.healthConsent = true // the grant itself triggers delivery, no extra call

        assertTrue(device.server.payload("anna", "workoutProgress").orEmpty().contains("anna-workout"))
        assertTrue(device.server.payload("anna", "profile").orEmpty().contains("anna-profile"))
        assertTrue(device.pendingSync.pendingDomains().isEmpty())
    }

    @Test
    fun guestDataIsNotUploadedOnFirstSignInWithoutConsent() = runBlocking {
        device.healthConsent = false
        device.recordData("guest") // before any account
        device.auth.signInAs("anna")

        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn("anna"))

        assertEquals(emptyList(), dataWrites("guest"))
        assertTrue(device.owner.isFullPushPending(), "the account still owes the device's copy")

        device.healthConsent = true
        assertTrue(device.server.payload("anna", "workoutProgress").orEmpty().contains("guest-workout"))
        assertFalse(device.owner.isFullPushPending())
    }

    @Test
    fun replacingTheAccountWithDeviceDataWithoutConsentSendsNothingAtAll() = runBlocking {
        device.server.seed("anna", "weight", """{"2026-09-01":70.0}""")
        device.healthConsent = false
        device.recordData("guest")
        device.auth.signInAs("anna")
        assertEquals(AccountDataOutcome.NeedsGuestChoice, device.coordinator.onSignedIn("anna"))

        assertEquals(AccountDataOutcome.Ready, device.coordinator.resolveGuestChoice("anna", GuestDataChoice.REPLACE_WITH_DEVICE_DATA))

        assertEquals(emptyList(), device.server.writes, "not even the erasures: the account is replaced whole or not at all")
        assertEquals("""{"2026-09-01":70.0}""", device.server.payload("anna", "weight"))
    }

    @Test
    fun signingOutWithoutConsentWarnsThatNothingIsBackedUp() = runBlocking {
        device.healthConsent = false
        signedIn("anna")
        device.recordData("anna")

        assertEquals(SignOutOutcome.Unsynced, device.coordinator.signOut(force = false))
        assertEquals("anna", device.auth.authState.value?.uid, "nothing wiped without the user's say")
    }

    @Test
    fun aSessionThatEndsByItselfKeepsDataTheServerLacks() = runBlocking {
        device.healthConsent = false
        signedIn("anna")
        device.recordData("anna")
        device.auth.authState.value = null // expired or revoked, the user did not sign out

        session.reconcile()

        assertTrue(device.workouts.current().containsKey("anna-workout"), "no copy exists anywhere else")
        assertEquals("anna", device.owner.ownerUid(), "anna gets it delivered on her next sign-in")
    }

    @Test
    fun startingOverStillErasesTheServerCopyWithoutConsent() = runBlocking {
        signedIn("anna")
        device.recordData("anna") // backed up while consent was given
        device.healthConsent = false // then withdrawn

        device.coordinator.resetUserData()

        assertFalse(device.server.payload("anna", "profile").orEmpty().contains("anna-profile"), "profile erased on the server")
        assertFalse(device.server.payload("anna", "workoutProgress").orEmpty().contains("anna-workout"))
    }

    @Test
    fun theConsentBelongsToThePersonNotTheDevice() = runBlocking {
        device.consentPrefs.forgetOnAccountChange = true
        signedIn("anna") // anna consented
        assertEquals(SignOutOutcome.SignedOut, device.coordinator.signOut(force = true))

        assertEquals(HealthDataConsentStatus.NOT_ASKED, device.consent.status(), "bob must be asked himself")
        signedIn("bob")
        device.recordData("bob")
        assertEquals(emptyList(), dataWrites("bob"), "nothing of bob's leaves under anna's consent")
    }

    @Test
    fun signingOutWithoutConsentButNothingWaitingIsNotAWarning() = runBlocking {
        signedIn("anna")
        device.recordData("anna") // fully backed up
        device.healthConsent = false // then withdrawn

        assertEquals(SignOutOutcome.SignedOut, device.coordinator.signOut(force = false))
    }

    @Test
    fun aSignOutTheUserAskedForIsFinishedAfterAnInterruptedWipe() = runBlocking {
        device.healthConsent = false
        signedIn("anna")
        device.recordData("anna") // never backed up
        // The app died between the sign-out and the wipe of "Sign out and delete".
        device.owner.setWipeRequested(true)
        device.auth.signOut()

        session.reconcile()

        assertTrue(device.workouts.current().isEmpty(), "the user asked for it to be deleted")
        assertEquals(null, device.owner.ownerUid())
    }

    @Test
    fun signingOutForgetsTheConsentEvenWithoutOwnedData() = runBlocking {
        device.consentPrefs.forgetOnAccountChange = true
        device.auth.signInAs("anna") // signed in, but the guest choice was never made: no owner
        assertEquals(null, device.owner.ownerUid())

        device.coordinator.signOut(force = false)

        assertEquals(HealthDataConsentStatus.NOT_ASKED, device.consent.status())
    }
}


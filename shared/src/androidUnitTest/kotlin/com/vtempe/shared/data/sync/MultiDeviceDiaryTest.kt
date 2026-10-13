package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataOutcome
import com.vtempe.shared.domain.account.SignOutOutcome
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * VT-AUD-057: a phone and a tablet signed in to one account each log their own diary entries;
 * the account keeps both instead of whichever device uploaded last.
 */
class MultiDeviceDiaryTest {
    private val server = FakeSyncServer()
    private val phone = TestDevice(server)
    private val tablet = TestDevice(server)

    @AfterTest
    fun tearDown() {
        phone.close()
        tablet.close()
    }

    private suspend fun signIn(device: TestDevice) {
        device.auth.signInAs("anna")
        assertEquals(AccountDataOutcome.Ready, device.coordinator.onSignedIn("anna"))
    }

    private fun weightDates(): Set<String> =
        server.payload("anna", SyncDomain.WEIGHT.wireKey)?.let { Json.parseToJsonElement(it).jsonObject.keys }.orEmpty()

    @Test
    fun entriesLoggedOnTwoDevicesAreBothKept() = runBlocking {
        signIn(phone)
        signIn(tablet)

        phone.weight.logWeight("2026-10-01", 70.0)
        phone.flushPushes()
        tablet.weight.logWeight("2026-10-02", 69.5) // the tablet never saw the phone's entry
        tablet.flushPushes()

        assertEquals(setOf("2026-10-01", "2026-10-02"), weightDates())
    }

    @Test
    fun aResetIsNotUndoneByAnotherDevicesLaterUpload() = runBlocking {
        signIn(phone)
        phone.weight.logWeight("2026-10-01", 70.0)
        phone.flushPushes()
        signIn(tablet) // the tablet now holds the phone's entry too

        phone.coordinator.resetUserData()
        tablet.weight.logWeight("2026-10-05", 69.0)
        tablet.flushPushes()

        assertEquals(setOf("2026-10-05"), weightDates(), "only what the tablet changed is sent, not its stale copy")
    }

    @Test
    fun signingOutDeliversByMergingNotByReplacing() = runBlocking {
        signIn(phone)
        signIn(tablet)
        phone.weight.logWeight("2026-10-01", 70.0)
        phone.flushPushes()
        tablet.weight.logWeight("2026-10-02", 69.5) // not uploaded yet

        assertEquals(SignOutOutcome.SignedOut, tablet.coordinator.signOut())

        assertEquals(setOf("2026-10-01", "2026-10-02"), weightDates(), "the tablet's backup kept the phone's entry")
    }

    @Test
    fun aDeviceUpgradedWithoutASyncBaseStillKeepsTheOtherDevicesEntries() = runBlocking {
        signIn(phone)
        signIn(tablet)
        phone.weight.logWeight("2026-10-01", 70.0)
        phone.flushPushes()
        tablet.syncBase.clear() // as on a device updated from a version without it

        tablet.weight.logWeight("2026-10-02", 69.5)
        tablet.flushPushes()

        assertEquals(setOf("2026-10-01", "2026-10-02"), weightDates())
    }

    @Test
    fun aServerWithoutMergingStillGetsTheWholeDiary() = runBlocking {
        signIn(phone)
        server.legacy = true

        phone.weight.logWeight("2026-10-01", 70.0)
        phone.flushPushes()

        assertEquals(setOf("2026-10-01"), weightDates())
        assertTrue(phone.pendingSync.pendingDomains().isEmpty(), "delivered, so nothing is left to retry")
    }
}

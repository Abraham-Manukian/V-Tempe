package com.vtempe.ui.presenter

import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.shared.domain.consent.HealthDataConsentPreferences
import com.vtempe.shared.domain.consent.HealthDataConsentRecord
import com.vtempe.shared.domain.consent.HealthDataConsentStatus
import com.vtempe.ui.navigation.Destination
import com.vtempe.ui.screens.destinationForExistingProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthConsentPresenterTest {

    private class InMemoryConsent(var record: HealthDataConsentRecord? = null) : HealthDataConsentPreferences {
        override fun getHealthDataConsent() = record
        override fun setHealthDataConsent(record: HealthDataConsentRecord) { this.record = record }
    }

    private fun manager(prefs: InMemoryConsent) =
        HealthDataConsentManager(prefs, currentVersion = "v1", nowMillis = { 77L })

    @Test
    fun existingUserWithoutDecisionIsAskedOnceBeforeHome() {
        val prefs = InMemoryConsent()
        val consent = manager(prefs)
        assertEquals(Destination.HealthConsent, destinationForExistingProfile(consent))

        HealthConsentPresenter(consent).withdraw() // "Not now"
        assertEquals(Destination.Home, destinationForExistingProfile(consent))
        assertFalse(consent.isHealthDataConsentGranted())
    }

    @Test
    fun grantedUserGoesStraightHome() {
        val consent = manager(InMemoryConsent())
        consent.grant()
        assertEquals(Destination.Home, destinationForExistingProfile(consent))
    }

    @Test
    fun outdatedConsentVersionAsksAgain() {
        val prefs = InMemoryConsent(HealthDataConsentRecord(granted = true, documentVersion = "v0", decidedAtMillis = 1L))
        assertEquals(Destination.HealthConsent, destinationForExistingProfile(manager(prefs)))
    }

    @Test
    fun settingsSwitchGrantsAndWithdrawsThroughTheSameGate() {
        val consent = manager(InMemoryConsent())
        val presenter = HealthConsentPresenter(consent)
        assertEquals(HealthDataConsentStatus.NOT_ASKED, presenter.state.value.status)
        assertNull(presenter.state.value.decidedAtMillis)

        presenter.grant()
        assertTrue(presenter.state.value.granted)
        assertEquals(77L, presenter.state.value.decidedAtMillis)
        assertTrue(consent.isHealthDataConsentGranted())

        presenter.withdraw()
        assertEquals(HealthDataConsentStatus.DECLINED, presenter.state.value.status)
        assertFalse(consent.isHealthDataConsentGranted())
    }
}

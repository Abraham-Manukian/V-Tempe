package com.vtempe.shared.domain.consent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HealthDataConsentManagerTest {

    private class InMemoryConsent(var record: HealthDataConsentRecord? = null) : HealthDataConsentPreferences {
        override fun getHealthDataConsent() = record
        override fun setHealthDataConsent(record: HealthDataConsentRecord) { this.record = record }
    }

    private fun manager(prefs: InMemoryConsent, version: String = "v2", now: Long = 1_000L) =
        HealthDataConsentManager(prefs, currentVersion = version, nowMillis = { now })

    @Test
    fun withoutAnyDecisionTheUserMustBeAskedAndAiStaysOff() {
        val consent = manager(InMemoryConsent())
        assertEquals(HealthDataConsentStatus.NOT_ASKED, consent.status())
        assertFalse(consent.isHealthDataConsentGranted())
        assertNull(consent.record())
    }

    @Test
    fun grantIsStoredWithCurrentVersionAndTimestamp() {
        val prefs = InMemoryConsent()
        manager(prefs, version = "v2", now = 42L).grant()
        assertEquals(HealthDataConsentRecord(granted = true, documentVersion = "v2", decidedAtMillis = 42L), prefs.record)
        assertTrue(manager(prefs).isHealthDataConsentGranted())
    }

    @Test
    fun withdrawalDisablesAiAndIsNotAskedAgain() {
        val prefs = InMemoryConsent()
        val consent = manager(prefs)
        consent.grant()
        consent.withdraw()
        assertEquals(HealthDataConsentStatus.DECLINED, consent.status())
        assertFalse(consent.isHealthDataConsentGranted())
        assertFalse(prefs.record!!.granted)
    }

    @Test
    fun grantForAnOlderDocumentVersionCountsAsNotAsked() {
        val prefs = InMemoryConsent(HealthDataConsentRecord(granted = true, documentVersion = "v1", decidedAtMillis = 1L))
        val consent = manager(prefs, version = "v2")
        assertEquals(HealthDataConsentStatus.NOT_ASKED, consent.status())
        assertFalse(consent.isHealthDataConsentGranted())
        assertNull(consent.record())
    }

    @Test
    fun unreadableStorageFailsClosed() {
        val broken = object : HealthDataConsentPreferences {
            override fun getHealthDataConsent(): HealthDataConsentRecord? = error("storage broken")
            override fun setHealthDataConsent(record: HealthDataConsentRecord) = Unit
        }
        val consent = HealthDataConsentManager(broken)
        assertFalse(consent.isHealthDataConsentGranted())
        assertEquals(HealthDataConsentStatus.NOT_ASKED, consent.status())
    }
}

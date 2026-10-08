package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.shared.domain.consent.HealthDataConsentRecord
import com.vtempe.shared.domain.consent.HealthDataConsentStatus
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HealthDataConsentPersistenceTest {

    /** Map-backed Settings: survives "app restarts" by building new repositories over it. */
    private fun settings(): Settings {
        val values = mutableMapOf<String, Any>()
        return Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
            when (method.name) {
                "getBooleanOrNull", "getStringOrNull", "getLongOrNull" -> values[args!![0]]
                "getBoolean" -> values[args!![0]] ?: args[1]
                "putBoolean", "putString", "putLong" -> { values[args!![0] as String] = args[1]; null }
                "remove" -> { values.remove(args!![0]); null }
                else -> error("Unexpected Settings call: ${method.name}")
            }
        } as Settings
    }

    @Test
    fun freshInstallHasNoDecision() {
        assertNull(SettingsPreferencesRepository(settings()).getHealthDataConsent())
    }

    @Test
    fun decisionWithVersionAndTimestampSurvivesRestart() {
        val storage = settings()
        HealthDataConsentManager(SettingsPreferencesRepository(storage), currentVersion = "v1", nowMillis = { 1234L }).grant()

        val restarted = SettingsPreferencesRepository(storage)
        assertEquals(
            HealthDataConsentRecord(granted = true, documentVersion = "v1", decidedAtMillis = 1234L),
            restarted.getHealthDataConsent()
        )
        assertEquals(HealthDataConsentStatus.GRANTED, HealthDataConsentManager(restarted, currentVersion = "v1").status())
    }

    @Test
    fun withdrawalOverwritesTheGrantAndKeepsAnalyticsConsentIndependent() {
        val storage = settings()
        val prefs = SettingsPreferencesRepository(storage)
        prefs.setAnalyticsConsent(true)
        val consent = HealthDataConsentManager(prefs, currentVersion = "v1", nowMillis = { 5L })
        consent.grant()
        consent.withdraw()

        assertEquals(HealthDataConsentStatus.DECLINED, consent.status())
        assertEquals(true, SettingsPreferencesRepository(storage).getAnalyticsConsent())
    }
}

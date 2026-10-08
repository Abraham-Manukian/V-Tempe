package com.vtempe.shared.data.analytics

import com.vtempe.shared.domain.repository.AnalyticsConsentPreferences
import com.vtempe.shared.domain.repository.AnalyticsRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConsentGatedAnalyticsRepositoryTest {

    private class FakeConsent(var granted: Boolean) : AnalyticsConsentPreferences {
        override fun getAnalyticsConsent(): Boolean = granted
        override fun setAnalyticsConsent(granted: Boolean) {
            this.granted = granted
        }
    }

    private class RecordingAnalytics : AnalyticsRepository {
        val events = mutableListOf<String>()
        val properties = mutableListOf<String>()
        val nonFatals = mutableListOf<Throwable>()
        val collectionStates = mutableListOf<Boolean>()

        override fun logEvent(name: String, params: Map<String, String>) {
            events += name
        }

        override fun setUserProperty(key: String, value: String?) {
            properties += key
        }

        override fun recordNonFatal(throwable: Throwable, message: String?) {
            nonFatals += throwable
        }

        override fun setCollectionEnabled(enabled: Boolean) {
            collectionStates += enabled
        }
    }

    @Test
    fun withoutConsentNothingIsSentAndCollectionIsOff() {
        val delegate = RecordingAnalytics()
        val analytics = ConsentGatedAnalyticsRepository(delegate, FakeConsent(granted = false))

        analytics.logEvent("chat_message_sent")
        analytics.setUserProperty("age_bucket", "25_34")

        assertTrue(delegate.events.isEmpty())
        assertTrue(delegate.properties.isEmpty())
        assertEquals(listOf(false), delegate.collectionStates)
    }

    @Test
    fun withConsentEventsPassThrough() {
        val delegate = RecordingAnalytics()
        val analytics = ConsentGatedAnalyticsRepository(delegate, FakeConsent(granted = true))

        analytics.logEvent("plan_generated")
        analytics.setUserProperty("goal", "LOSE_FAT")

        assertEquals(listOf("plan_generated"), delegate.events)
        assertEquals(listOf("goal"), delegate.properties)
        assertEquals(listOf(true), delegate.collectionStates)
    }

    @Test
    fun revokingConsentStopsEventsAndDisablesCollection() {
        val delegate = RecordingAnalytics()
        val consent = FakeConsent(granted = true)
        val analytics = ConsentGatedAnalyticsRepository(delegate, consent)
        analytics.logEvent("first")

        consent.setAnalyticsConsent(false)
        analytics.logEvent("second")

        assertEquals(listOf("first"), delegate.events)
        assertEquals(listOf(true, false), delegate.collectionStates)
    }

    @Test
    fun callersCannotForceCollectionOnWithoutConsent() {
        val delegate = RecordingAnalytics()
        val analytics = ConsentGatedAnalyticsRepository(delegate, FakeConsent(granted = false))

        analytics.setCollectionEnabled(true)

        assertEquals(listOf(false), delegate.collectionStates)
    }

    @Test
    fun crashDiagnosticsAreNotConsentGated() {
        val delegate = RecordingAnalytics()
        val analytics = ConsentGatedAnalyticsRepository(delegate, FakeConsent(granted = false))

        analytics.recordNonFatal(IllegalStateException("boom"))

        assertEquals(1, delegate.nonFatals.size)
    }
}

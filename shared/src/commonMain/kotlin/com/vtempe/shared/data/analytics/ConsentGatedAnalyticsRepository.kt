package com.vtempe.shared.data.analytics

import com.vtempe.shared.domain.repository.AnalyticsConsentPreferences
import com.vtempe.shared.domain.repository.AnalyticsRepository

/**
 * Enforces the user's analytics opt-in for EVERY analytics call, not only for the demographic
 * user properties: without consent no events or user properties leave the device, and the
 * backend's automatic collection is switched off. Consent is re-read on each call, so a change
 * made in Settings applies from the next analytics call on (and from app start).
 *
 * Crash reporting ([recordNonFatal]) is deliberately NOT gated here — it is diagnostics, not
 * usage statistics, and the backend implementation is responsible for keeping it free of
 * user content.
 */
class ConsentGatedAnalyticsRepository(
    private val delegate: AnalyticsRepository,
    private val consent: AnalyticsConsentPreferences,
) : AnalyticsRepository {

    private var appliedCollectionState: Boolean? = null

    init {
        syncCollectionState()
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        if (syncCollectionState()) delegate.logEvent(name, params)
    }

    override fun setUserProperty(key: String, value: String?) {
        if (syncCollectionState()) delegate.setUserProperty(key, value)
    }

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        delegate.recordNonFatal(throwable, message)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        // Consent is the single source of truth — callers cannot enable collection around it.
        syncCollectionState()
    }

    /** Pushes the current consent to the delegate when it changed; returns whether it is granted. */
    private fun syncCollectionState(): Boolean {
        val granted = runCatching { consent.getAnalyticsConsent() }.getOrDefault(false)
        if (appliedCollectionState != granted) {
            delegate.setCollectionEnabled(granted)
            appliedCollectionState = granted
        }
        return granted
    }
}

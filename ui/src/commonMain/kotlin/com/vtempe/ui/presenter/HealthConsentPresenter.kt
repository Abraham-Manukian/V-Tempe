package com.vtempe.ui.presenter

import com.vtempe.shared.domain.consent.HealthDataConsentManager
import com.vtempe.shared.domain.consent.HealthDataConsentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HealthConsentState(
    val status: HealthDataConsentStatus = HealthDataConsentStatus.NOT_ASKED,
    /** When the current decision was made; null while the user has not been asked. */
    val decidedAtMillis: Long? = null
) {
    val granted: Boolean get() = status == HealthDataConsentStatus.GRANTED
}

/**
 * Backs the launch-time consent request for existing users and the consent row in Settings.
 * Every change goes through [HealthDataConsentManager], the same object the AI gates read, so
 * the UI and the gate cannot disagree.
 */
class HealthConsentPresenter(private val consent: HealthDataConsentManager) {

    private val _state = MutableStateFlow(read())
    val state: StateFlow<HealthConsentState> = _state.asStateFlow()

    fun grant() {
        consent.grant()
        _state.value = read()
    }

    /** Declining the first request and withdrawing a grant are the same decision. */
    fun withdraw() {
        consent.withdraw()
        _state.value = read()
    }

    private fun read() = HealthConsentState(
        status = consent.status(),
        decidedAtMillis = consent.record()?.decidedAtMillis
    )
}

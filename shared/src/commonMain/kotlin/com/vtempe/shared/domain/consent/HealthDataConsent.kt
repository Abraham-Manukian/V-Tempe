package com.vtempe.shared.domain.consent

import com.vtempe.shared.domain.legal.LegalDocuments
import kotlinx.datetime.Clock

/**
 * The user's last decision on the consent to process health data (special category of personal
 * data, 152-FZ art. 10) and to transfer it abroad (art. 12) for the AI coach.
 */
data class HealthDataConsentRecord(
    val granted: Boolean,
    /** [LegalDocuments.HEALTH_DATA_CONSENT_VERSION] the user saw when deciding. */
    val documentVersion: String,
    val decidedAtMillis: Long
)

/** Narrow storage interface for the consent decision; implemented by the preferences store. */
interface HealthDataConsentPreferences {
    fun getHealthDataConsent(): HealthDataConsentRecord?
    fun setHealthDataConsent(record: HealthDataConsentRecord)
    fun clearHealthDataConsent()
}

enum class HealthDataConsentStatus {
    /** No decision for the current document version — the user must be asked. */
    NOT_ASKED,
    GRANTED,
    /** Declined or withdrawn: AI features that send health data stay disabled. */
    DECLINED
}

/** Read-only view used by the gates in front of every AI request. */
fun interface HealthDataConsentGate {
    fun isHealthDataConsentGranted(): Boolean
}

/**
 * Owns the consent rules: a decision only counts for the document version it was given for,
 * and anything but an explicit grant keeps the AI features off.
 */
class HealthDataConsentManager(
    private val preferences: HealthDataConsentPreferences,
    private val currentVersion: String = LegalDocuments.HEALTH_DATA_CONSENT_VERSION,
    private val nowMillis: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Runs after a grant: the backup the consent covers starts at once (DI delivers the held-back
     *  sync changes), not at the next launch. */
    private val onGranted: () -> Unit = {},
) : HealthDataConsentGate {

    fun status(): HealthDataConsentStatus {
        val record = runCatching { preferences.getHealthDataConsent() }.getOrNull()
        return when {
            record == null || record.documentVersion != currentVersion -> HealthDataConsentStatus.NOT_ASKED
            record.granted -> HealthDataConsentStatus.GRANTED
            else -> HealthDataConsentStatus.DECLINED
        }
    }

    fun record(): HealthDataConsentRecord? =
        runCatching { preferences.getHealthDataConsent() }.getOrNull()
            ?.takeIf { it.documentVersion == currentVersion }

    override fun isHealthDataConsentGranted(): Boolean = status() == HealthDataConsentStatus.GRANTED

    fun grant() {
        decide(granted = true)
        onGranted()
    }

    /**
     * Forgets the decision, so the next person is asked: the consent is a person's, not the
     * device's. Called when the device data stops belonging to the account that decided (sign-out,
     * another account, account deletion); until asked again, nothing is sent (NOT_ASKED).
     */
    fun forget() = preferences.clearHealthDataConsent()

    /** Used both for declining the first request and for withdrawing an earlier grant. */
    fun withdraw() = decide(granted = false)

    private fun decide(granted: Boolean) {
        preferences.setHealthDataConsent(
            HealthDataConsentRecord(granted = granted, documentVersion = currentVersion, decidedAtMillis = nowMillis())
        )
    }
}

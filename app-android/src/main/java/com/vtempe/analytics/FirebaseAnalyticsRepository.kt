package com.vtempe.analytics

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.vtempe.shared.domain.repository.AnalyticsRepository

/**
 * Real Firebase-backed implementation. Only ever constructed after
 * [FirebaseApp.initializeApp] has succeeded (see [createAnalyticsRepository]) — safe to assume
 * both [Firebase.analytics] and [Firebase.crashlytics] are ready to use here.
 */
class FirebaseAnalyticsRepository(context: Context) : AnalyticsRepository {

    private val analytics: FirebaseAnalytics = Firebase.analytics
    private val crashlytics: FirebaseCrashlytics = Firebase.crashlytics

    init {
        // Ensure Firebase is initialized for this context (idempotent if already done by the
        // FirebaseInitProvider ContentProvider — this is a defensive no-op in that case).
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    override fun logEvent(name: String, params: Map<String, String>) {
        val bundle = Bundle().apply {
            params.forEach { (key, value) -> putString(key, value) }
        }
        analytics.logEvent(name, bundle)
    }

    override fun setUserProperty(key: String, value: String?) {
        analytics.setUserProperty(key, value)
    }

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        message?.let { crashlytics.log(it) }
        crashlytics.recordException(throwable.withoutMessages())
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        analytics.setAnalyticsCollectionEnabled(enabled)
        // On opt-out also drop the app-instance id and any user properties already queued.
        if (!enabled) analytics.resetAnalyticsData()
    }
}

/**
 * Exception messages are free text we don't control: Ktor's ClientRequestException embeds the
 * server's response body, SerializationException embeds a fragment of the JSON (an AI reply can
 * echo the user's health data). Non-fatal reports keep the exception TYPES and stack traces —
 * all that's needed to find the bug — but never the messages.
 */
private fun Throwable.withoutMessages(): Throwable {
    val original = this
    return RedactedNonFatal(original::class.java.name, original.cause?.withoutMessages()).apply {
        stackTrace = original.stackTrace
    }
}

private class RedactedNonFatal(originalType: String, cause: Throwable?) : Exception(originalType, cause)

/**
 * Builds an [AnalyticsRepository], falling back to a no-op logger if the Firebase project
 * isn't configured yet (no google-services.json processed at build time — see
 * app-android/build.gradle.kts). Never throws.
 */
fun createAnalyticsRepository(context: Context): AnalyticsRepository =
    runCatching { FirebaseAnalyticsRepository(context) }
        .getOrElse { com.vtempe.shared.data.stub.NoOpAnalyticsRepository() }

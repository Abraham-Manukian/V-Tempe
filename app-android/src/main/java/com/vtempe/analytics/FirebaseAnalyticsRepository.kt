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
        crashlytics.recordException(throwable)
    }
}

/**
 * Builds an [AnalyticsRepository], falling back to a no-op logger if the Firebase project
 * isn't configured yet (no google-services.json processed at build time — see
 * app-android/build.gradle.kts). Never throws.
 */
fun createAnalyticsRepository(context: Context): AnalyticsRepository =
    runCatching { FirebaseAnalyticsRepository(context) }
        .getOrElse { com.vtempe.shared.data.stub.NoOpAnalyticsRepository() }

package com.vtempe.shared.data.stub

import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.AuthUser
import com.vtempe.shared.domain.repository.PurchasesRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * Offline-first stubs used until real platform implementations are wired in.
 *
 * PurchasesRepository  → replace with AndroidPurchasesRepository / SKProductsStore
 * AdviceRepository     → replace with NetworkAdviceRepository when advice endpoint ships
 * AuthRepository       → replace with FirebaseAuthRepository in Android's AppModule.kt;
 *                         iOS keeps this stub until Firebase iOS is wired.
 */

class StubAdviceRepository : AdviceRepository {
    private val adviceMap = MutableStateFlow<Map<String, Advice>>(emptyMap())

    override suspend fun getAdvice(profile: Profile, context: Map<String, Any?>): Advice {
        val topic = (context["topic"] as? String)?.lowercase() ?: "general"
        val current = adviceMap.value[topic]
        if (current != null) return current
        val defaults = defaultAdvice(topic)
        adviceMap.value = adviceMap.value + (topic to defaults)
        return defaults
    }

    override suspend fun saveAdvice(topic: String, advice: Advice) {
        adviceMap.value = adviceMap.value + (topic.lowercase() to advice)
    }

    override fun observeAdvice(topic: String): Flow<Advice> =
        adviceMap.map { it[topic.lowercase()] ?: defaultAdvice(topic.lowercase()) }

    override suspend fun hasAdvice(topic: String): Boolean =
        true  // Stub always has default advice — prevents bootstrap on every launch

    private fun defaultAdvice(topic: String): Advice = Advice(messages = emptyList())
}

/**
 * Alpha / free tier: subscription always active.
 * Replace with AndroidPurchasesRepository (Google Play Billing) before paid launch.
 */
class StubPurchasesRepository : PurchasesRepository {
    override suspend fun isSubscriptionActive(): Boolean = true
}

/** Used on iOS (Firebase iOS not wired yet) and Android builds without google-services.json —
 *  sign-up/sign-in always fail with a clear message rather than silently pretending to work. */
class StubAuthRepository : AuthRepository {
    override val authState: StateFlow<AuthUser?> = MutableStateFlow(null).asStateFlow()

    override suspend fun signUp(email: String, password: String): AuthUser =
        throw AuthException(AuthErrorCode.UNAVAILABLE, "Auth is not available in this build")

    override suspend fun signIn(email: String, password: String): AuthUser =
        throw AuthException(AuthErrorCode.UNAVAILABLE, "Auth is not available in this build")

    override suspend fun signInWithGoogle(idToken: String): AuthUser =
        throw AuthException(AuthErrorCode.UNAVAILABLE, "Auth is not available in this build")

    override suspend fun signInWithApple(idToken: String, rawNonce: String): AuthUser =
        throw AuthException(AuthErrorCode.UNAVAILABLE, "Auth is not available in this build")

    override suspend fun signOut() = Unit

    override suspend fun idToken(): String? = null
}

/**
 * No-op analytics used on platforms/builds without a wired backend (iOS, or Android
 * without google-services.json). Logs to Napier so events are still visible in dev builds.
 * Replace with FirebaseAnalyticsRepository once google-services.json is added.
 */
class NoOpAnalyticsRepository : AnalyticsRepository {
    override fun logEvent(name: String, params: Map<String, String>) {
        Napier.d(tag = "Analytics", message = "event=$name params=$params")
    }

    override fun setUserProperty(key: String, value: String?) {
        Napier.d(tag = "Analytics", message = "userProperty $key=$value")
    }

    override fun recordNonFatal(throwable: Throwable, message: String?) {
        Napier.e(tag = "Analytics", message = message ?: "non-fatal", throwable = throwable)
    }
}

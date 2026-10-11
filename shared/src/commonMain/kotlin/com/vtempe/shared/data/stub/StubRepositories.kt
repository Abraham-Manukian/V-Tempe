package com.vtempe.shared.data.stub

import com.vtempe.shared.domain.repository.AnalyticsRepository
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.AuthUser
import com.vtempe.shared.domain.repository.PurchasesRepository
import com.vtempe.shared.domain.repository.ReauthCredential
import io.github.aakira.napier.Napier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Offline-first stubs used until real platform implementations are wired in.
 *
 * PurchasesRepository  → replace with AndroidPurchasesRepository / SKProductsStore
 * AuthRepository       → replace with FirebaseAuthRepository in Android's AppModule.kt;
 *                         iOS keeps this stub until Firebase iOS is wired.
 */

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
    override val isAvailable: Boolean = false
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
    override suspend fun idTokenFor(uid: String): String? = null

    override suspend fun reauthenticate(credential: ReauthCredential): Unit =
        throw AuthException(AuthErrorCode.UNAVAILABLE, "Auth is not available in this build")

    override suspend fun deleteCurrentUser(): Unit =
        throw AuthException(AuthErrorCode.UNAVAILABLE, "Auth is not available in this build")
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

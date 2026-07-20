package com.vtempe.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.AuthUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

private fun FirebaseUser?.toAuthUser(): AuthUser? = this?.let { AuthUser(uid = it.uid, email = it.email) }

/**
 * Real Firebase-backed implementation. Only ever constructed after confirming a [FirebaseApp]
 * exists (see [createAuthRepository]) — mirrors [com.vtempe.analytics.FirebaseAnalyticsRepository].
 */
class FirebaseAuthRepository : AuthRepository {

    private val auth: FirebaseAuth = Firebase.auth
    private val _authState = MutableStateFlow(auth.currentUser.toAuthUser())
    override val authState: StateFlow<AuthUser?> = _authState.asStateFlow()

    init {
        auth.addAuthStateListener { firebaseAuth ->
            _authState.value = firebaseAuth.currentUser.toAuthUser()
        }
    }

    override suspend fun signUp(email: String, password: String): AuthUser =
        runCatching {
            auth.createUserWithEmailAndPassword(email, password).await().user.toAuthUser()
                ?: error("Firebase returned no user after sign-up")
        }.getOrElse { throw it.toAuthException() }

    override suspend fun signIn(email: String, password: String): AuthUser =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).await().user.toAuthUser()
                ?: error("Firebase returned no user after sign-in")
        }.getOrElse { throw it.toAuthException() }

    override suspend fun signInWithGoogle(idToken: String): AuthUser =
        runCatching {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await().user.toAuthUser()
                ?: error("Firebase returned no user after Google sign-in")
        }.getOrElse { throw it.toAuthException() }

    override suspend fun signInWithApple(idToken: String, rawNonce: String): AuthUser =
        runCatching {
            val credential = OAuthProvider.newCredentialBuilder("apple.com")
                .setIdTokenWithRawNonce(idToken, rawNonce)
                .build()
            auth.signInWithCredential(credential).await().user.toAuthUser()
                ?: error("Firebase returned no user after Apple sign-in")
        }.getOrElse { throw it.toAuthException() }

    override suspend fun signOut() {
        auth.signOut()
    }

    override suspend fun idToken(): String? =
        // getIdToken(false) refreshes internally only if the cached token has actually expired —
        // this is NOT "always force a network round-trip", it's the standard cheap call.
        runCatching { auth.currentUser?.getIdToken(false)?.await()?.token }.getOrNull()

    private fun Throwable.toAuthException(): AuthException = when (this) {
        is FirebaseAuthWeakPasswordException -> AuthException(AuthErrorCode.WEAK_PASSWORD, "Password is too weak", this)
        is FirebaseAuthInvalidCredentialsException -> AuthException(AuthErrorCode.INVALID_CREDENTIALS, "Invalid email or password", this)
        is FirebaseAuthUserCollisionException -> AuthException(AuthErrorCode.EMAIL_IN_USE, "An account with this email already exists", this)
        is FirebaseNetworkException -> AuthException(AuthErrorCode.NETWORK, "Network error, please try again", this)
        else -> AuthException(AuthErrorCode.UNKNOWN, message ?: "Authentication failed", this)
    }
}

/**
 * Builds an [AuthRepository], falling back to [com.vtempe.shared.data.stub.StubAuthRepository]
 * if the Firebase project isn't configured yet (no google-services.json processed at build
 * time — see app-android/build.gradle.kts). Never throws.
 */
fun createAuthRepository(): AuthRepository =
    runCatching { FirebaseAuthRepository() }
        .getOrElse { com.vtempe.shared.data.stub.StubAuthRepository() }

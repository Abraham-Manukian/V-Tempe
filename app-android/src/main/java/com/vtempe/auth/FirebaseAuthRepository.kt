package com.vtempe.auth

import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
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
import com.vtempe.shared.domain.repository.ReauthCredential
import com.vtempe.shared.domain.repository.SignInMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.CancellationException

private const val APPLE_PROVIDER_ID = "apple.com"

private fun FirebaseUser?.toAuthUser(): AuthUser? = this?.let {
    // Google Sign-In provides a profile photo + display name; email/password and Apple typically don't.
    AuthUser(
        uid = it.uid,
        email = it.email,
        photoUrl = it.photoUrl?.toString(),
        displayName = it.displayName?.takeIf { n -> n.isNotBlank() },
        signInMethod = it.signInMethod()
    )
}

/** providerData also lists the generic "firebase" entry; the first known provider wins. */
private fun FirebaseUser.signInMethod(): SignInMethod? =
    providerData.firstNotNullOfOrNull { info ->
        when (info.providerId) {
            GoogleAuthProvider.PROVIDER_ID -> SignInMethod.GOOGLE
            EmailAuthProvider.PROVIDER_ID -> SignInMethod.PASSWORD
            APPLE_PROVIDER_ID -> SignInMethod.APPLE
            else -> null
        }
    }

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
            val credential = OAuthProvider.newCredentialBuilder(APPLE_PROVIDER_ID)
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
        runCatching { auth.currentUser?.getIdToken(false)?.await()?.token }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()

    override suspend fun idTokenFor(uid: String): String? {
        // Token taken from the same FirebaseUser object that passed the uid check.
        val user = auth.currentUser?.takeIf { it.uid == uid } ?: return null
        return runCatching { user.getIdToken(false).await().token }
            .onFailure { if (it is CancellationException) throw it }
            .getOrNull()
    }

    override suspend fun reauthenticate(credential: ReauthCredential) {
        val user = auth.currentUser ?: throw AuthException(AuthErrorCode.UNKNOWN, "No signed-in user")
        runCatching { user.reauthenticate(credential.toFirebaseCredential(user)).await() }
            .getOrElse { error ->
                // Firebase reports another account's credential ("user mismatch") this way.
                if (error is FirebaseAuthInvalidUserException) {
                    throw AuthException(AuthErrorCode.INVALID_CREDENTIALS, "Credential belongs to another user", error)
                }
                throw error.toAuthException()
            }
    }

    override suspend fun deleteCurrentUser() {
        val user = auth.currentUser ?: throw AuthException(AuthErrorCode.UNKNOWN, "No signed-in user")
        runCatching { user.delete().await() }.getOrElse { throw it.toAuthException() }
    }

    private fun ReauthCredential.toFirebaseCredential(user: FirebaseUser): AuthCredential = when (this) {
        is ReauthCredential.Password -> EmailAuthProvider.getCredential(
            user.email ?: throw AuthException(AuthErrorCode.INVALID_CREDENTIALS, "Account has no email"),
            password
        )
        is ReauthCredential.Google -> GoogleAuthProvider.getCredential(idToken, null)
        is ReauthCredential.Apple -> OAuthProvider.newCredentialBuilder(APPLE_PROVIDER_ID)
            .setIdTokenWithRawNonce(idToken, rawNonce)
            .build()
    }

    private fun Throwable.toAuthException(): AuthException = when (this) {
        is CancellationException -> throw this
        is AuthException -> this
        is FirebaseAuthRecentLoginRequiredException ->
            AuthException(AuthErrorCode.REQUIRES_RECENT_LOGIN, "Recent sign-in required", this)
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

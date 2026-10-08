package com.vtempe.shared.domain.repository

import kotlinx.coroutines.flow.StateFlow

data class AuthUser(
    val uid: String,
    val email: String?,
    val photoUrl: String? = null,
    val displayName: String? = null,
    /** How the user signs in; null when it can't be determined. Decides how to re-authenticate. */
    val signInMethod: SignInMethod? = null
)

enum class SignInMethod { PASSWORD, GOOGLE, APPLE }

/** A fresh proof of identity for [AuthRepository.reauthenticate], from the user's own sign-in method. */
sealed interface ReauthCredential {
    data class Password(val password: String) : ReauthCredential
    data class Google(val idToken: String) : ReauthCredential
    /** [rawNonce]: see [AuthRepository.signInWithApple]. */
    data class Apple(val idToken: String, val rawNonce: String) : ReauthCredential
}

/** Machine-readable reason, so the UI layer can show a LOCALIZED message via string resources —
 *  [message] is an English fallback only (logs, non-UI contexts), never shown to the user
 *  directly (this is an RU-first app). */
enum class AuthErrorCode {
    INVALID_CREDENTIALS, WEAK_PASSWORD, EMAIL_IN_USE, NETWORK, UNAVAILABLE, UNKNOWN,

    /** A sensitive operation (account deletion) needs a recent sign-in; see [AuthRepository.reauthenticate]. */
    REQUIRES_RECENT_LOGIN
}

class AuthException(
    val code: AuthErrorCode,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Firebase-backed account auth — separate from [PurchasesRepository] (store billing) and
 * [ProfileRepository] (fitness profile data). Platforms without a wired Firebase project (iOS,
 * or Android builds without google-services.json) get [com.vtempe.shared.data.stub.StubAuthRepository].
 */
interface AuthRepository {
    /** null = signed out. Emits on every sign-in/sign-out. */
    val authState: StateFlow<AuthUser?>

    /** Throws [AuthException] on failure. */
    suspend fun signUp(email: String, password: String): AuthUser

    /** Throws [AuthException] on failure. */
    suspend fun signIn(email: String, password: String): AuthUser

    /** Exchanges a Google ID token (obtained on-device via Credential Manager) for a Firebase
     *  session. Throws [AuthException] on failure. */
    suspend fun signInWithGoogle(idToken: String): AuthUser

    /** Exchanges an Apple identity token (obtained on-device via AuthenticationServices) for a
     *  Firebase session. [rawNonce] is the unhashed nonce that was SHA-256-hashed into the
     *  original Apple authorization request — required so Firebase can verify the token wasn't
     *  replayed. Throws [AuthException] on failure. */
    suspend fun signInWithApple(idToken: String, rawNonce: String): AuthUser

    suspend fun signOut()

    /** A fresh Firebase ID token for `Authorization: Bearer` auth, or null when signed out or
     *  unavailable. Implementations own caching/refresh internally (the Firebase SDK already
     *  does this — callers should call this once per request, not cache it themselves). */
    suspend fun idToken(): String?

    /** Like [idToken], but only if [uid] is still the signed-in user — taken from that same user
     *  object, so a sign-in switch can't slip in between the check and the token. */
    suspend fun idTokenFor(uid: String): String?

    /** Refreshes the signed-in user's sign-in with [credential], which must belong to the same
     *  user. Throws [AuthException] on failure ([AuthErrorCode.INVALID_CREDENTIALS] for a wrong
     *  password or another user's credential). */
    suspend fun reauthenticate(credential: ReauthCredential)

    /** Permanently deletes the signed-in user, which signs them out. Throws [AuthException] —
     *  [AuthErrorCode.REQUIRES_RECENT_LOGIN] when the last sign-in is too old: [reauthenticate],
     *  then call this again. */
    suspend fun deleteCurrentUser()
}

package com.vtempe.ui.screens

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named

/** A Google ID token from Credential Manager, or null if the user closed the picker. Throws
 *  [AuthException]: [AuthErrorCode.UNAVAILABLE] when no web client id is configured (see
 *  app-android/build.gradle.kts GOOGLE_WEB_CLIENT_ID), [AuthErrorCode.UNKNOWN] when the request fails. */
internal suspend fun requestGoogleIdToken(context: Context): String? {
    // GlobalContext, not KoinProvider — KoinProvider.koin is only ever assigned on iOS (see its
    // kdoc); on Android, Koin is started via the standard androidContext()/startKoin{} path and
    // lives in GlobalContext, so KoinProvider.koin is always null here. Using it silently made
    // this always resolve to a blank id and report AuthErrorCode.UNAVAILABLE.
    val webClientId = GlobalContext.getOrNull()?.get<String>(named("googleWebClientId")).orEmpty()
    if (webClientId.isBlank()) {
        throw AuthException(AuthErrorCode.UNAVAILABLE, "Google web client id is not configured")
    }

    val option = GetSignInWithGoogleOption.Builder(webClientId)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    return try {
        val result = CredentialManager.create(context).getCredential(context, request)
        GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    } catch (e: GetCredentialCancellationException) {
        null // user closed the picker — not an error
    } catch (e: GetCredentialException) {
        android.util.Log.w("GoogleSignIn", "Credential request failed: ${e.type}")
        throw AuthException(AuthErrorCode.UNKNOWN, "Google credential request failed", e)
    } catch (e: GoogleIdTokenParsingException) {
        android.util.Log.w("GoogleSignIn", "Google ID credential could not be parsed")
        throw AuthException(AuthErrorCode.UNKNOWN, "Google ID credential could not be parsed", e)
    }
}

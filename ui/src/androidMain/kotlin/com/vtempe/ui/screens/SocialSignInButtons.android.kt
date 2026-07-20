package com.vtempe.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.ui.*
import com.vtempe.ui.presenter.AuthPresenter
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.core.qualifier.named

/** Android's half of the sign-in options — Google via Credential Manager (Google's current
 *  recommended API, replacing the deprecated GoogleSignInClient). No Apple button here: Sign in
 *  with Apple is iOS-only per product decision, see [SocialSignInButtons] (iOS actual). */
@Composable
actual fun SocialSignInButtons(presenter: AuthPresenter) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            scope.launch {
                loading = true
                val token = requestGoogleIdToken(context, presenter)
                loading = false
                if (token != null) presenter.signInWithGoogle(token)
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading
    ) {
        Text(stringResource(Res.string.auth_continue_with_google))
    }
}

/** Returns the Google ID token, or null if the user cancelled or no web client id is configured
 *  (see app-android/build.gradle.kts GOOGLE_WEB_CLIENT_ID). Reports real failures to [presenter]
 *  itself since they never touch [com.vtempe.shared.domain.repository.AuthRepository]. */
private suspend fun requestGoogleIdToken(context: Context, presenter: AuthPresenter): String? {
    val webClientId = KoinProvider.koin?.get<String>(named("googleWebClientId")).orEmpty()
    if (webClientId.isBlank()) {
        presenter.reportError(AuthErrorCode.UNAVAILABLE)
        return null
    }

    val option = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .build()
    val request = GetCredentialRequest.Builder().addCredentialOption(option).build()

    return try {
        val result = CredentialManager.create(context).getCredential(context, request)
        GoogleIdTokenCredential.createFrom(result.credential.data).idToken
    } catch (e: GetCredentialCancellationException) {
        null // user closed the picker — not an error
    } catch (e: GetCredentialException) {
        presenter.reportError(AuthErrorCode.UNKNOWN)
        null
    } catch (e: GoogleIdTokenParsingException) {
        presenter.reportError(AuthErrorCode.UNKNOWN)
        null
    }
}

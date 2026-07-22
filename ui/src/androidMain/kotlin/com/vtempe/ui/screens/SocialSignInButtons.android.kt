package com.vtempe.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

    // Google's own brand guidelines call for a plain white button with the "G" mark — matches
    // what users already recognize from every other app's sign-in screen, and (unlike an
    // OutlinedButton tinted to the app's brand colour) stays legible on any background.
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
        enabled = !loading,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F)
        ),
        border = BorderStroke(1.dp, Color(0xFFDADCE0))
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color(0xFF1F1F1F), strokeWidth = 2.dp)
        } else {
            GoogleLogo()
            Spacer(Modifier.width(12.dp))
            Text(stringResource(Res.string.auth_continue_with_google), fontWeight = FontWeight.Medium)
        }
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

package com.vtempe.ui.screens.settings

import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.repository.ReauthCredential
import com.vtempe.shared.domain.repository.SignInMethod

/** The platform's social sign-in, reused to confirm the user's identity before account deletion. */
internal interface ProviderReauthentication {
    /** The provider this platform signs in with: Google on Android, Apple on iOS. */
    val method: SignInMethod

    /** A fresh credential, or null if the user cancelled. Throws
     *  [com.vtempe.shared.domain.repository.AuthException] when the provider fails. */
    suspend fun requestCredential(): ReauthCredential?
}

@Composable
internal expect fun rememberProviderReauthentication(): ProviderReauthentication

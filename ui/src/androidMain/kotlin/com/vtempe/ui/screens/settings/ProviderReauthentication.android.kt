package com.vtempe.ui.screens.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vtempe.shared.domain.repository.ReauthCredential
import com.vtempe.shared.domain.repository.SignInMethod
import com.vtempe.ui.screens.requestGoogleIdToken

private class GoogleReauthentication(private val context: Context) : ProviderReauthentication {
    override val method = SignInMethod.GOOGLE

    override suspend fun requestCredential(): ReauthCredential? =
        requestGoogleIdToken(context)?.let { ReauthCredential.Google(it) }
}

@Composable
internal actual fun rememberProviderReauthentication(): ProviderReauthentication {
    val context = LocalContext.current
    return remember(context) { GoogleReauthentication(context) }
}

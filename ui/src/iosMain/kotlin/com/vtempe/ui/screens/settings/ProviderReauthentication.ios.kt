package com.vtempe.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.ReauthCredential
import com.vtempe.shared.domain.repository.SignInMethod
import com.vtempe.ui.screens.requestAppleIdToken
import com.vtempe.ui.screens.secureRandomNonce
import kotlinx.coroutines.CancellationException

private class AppleReauthentication : ProviderReauthentication {
    override val method = SignInMethod.APPLE

    override suspend fun requestCredential(): ReauthCredential? {
        val rawNonce = secureRandomNonce()
        val idToken = try {
            requestAppleIdToken(rawNonce)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            throw AuthException(AuthErrorCode.UNKNOWN, error.message ?: "Apple sign-in failed", error)
        }
        return idToken?.let { ReauthCredential.Apple(it, rawNonce) }
    }
}

@Composable
internal actual fun rememberProviderReauthentication(): ProviderReauthentication =
    remember { AppleReauthentication() }

package com.vtempe.ui.screens

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
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.ui.*
import com.vtempe.ui.presenter.AuthPresenter
import com.vtempe.ui.util.sha256Hex
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jetbrains.compose.resources.stringResource
import platform.AuthenticationServices.ASAuthorization
import platform.AuthenticationServices.ASAuthorizationAppleIDCredential
import platform.AuthenticationServices.ASAuthorizationAppleIDProvider
import platform.AuthenticationServices.ASAuthorizationController
import platform.AuthenticationServices.ASAuthorizationControllerDelegateProtocol
import platform.AuthenticationServices.ASAuthorizationControllerPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASAuthorizationScopeEmail
import platform.AuthenticationServices.ASAuthorizationScopeFullName
import platform.AuthenticationServices.ASPresentationAnchor
import platform.Foundation.NSError
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIApplication
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * iOS's half of the sign-in options — Apple via AuthenticationServices, per product decision
 * (Google Sign-In stays Android-only, see the Android actual of [SocialSignInButtons]).
 *
 * IMPORTANT — not end-to-end functional yet: this reaches Apple's picker and gets a real
 * identity token, then forwards it to [presenter], which calls
 * [com.vtempe.shared.domain.repository.AuthRepository.signInWithApple]. But iOS has no Firebase
 * SDK wired into the Xcode project yet (only Android has google-services.json processed), so
 * [com.vtempe.shared.data.stub.StubAuthRepository] is what actually receives the call today and
 * throws [AuthErrorCode.UNAVAILABLE]. Finishing this requires, on a Mac with Xcode: adding the
 * Firebase iOS SDK (Swift Package Manager: firebase-ios-sdk, FirebaseAuth product) to the iosApp
 * target, dropping in a GoogleService-Info.plist from the Firebase console, and enabling both
 * "Sign in with Apple" (Xcode > Signing & Capabilities) and the Apple provider (Firebase console
 * > Authentication > Sign-in method) — this environment has no Mac/Xcode to do that part.
 */
@Composable
actual fun SocialSignInButtons(presenter: AuthPresenter) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            scope.launch {
                loading = true
                val rawNonce = secureRandomNonce()
                runCatching { requestAppleIdToken(rawNonce) }
                    .onSuccess { idToken -> if (idToken != null) presenter.signInWithApple(idToken, rawNonce) }
                    .onFailure { presenter.reportError(AuthErrorCode.UNKNOWN) }
                loading = false
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !loading
    ) {
        Text(stringResource(Res.string.auth_continue_with_apple))
    }
}

/** Bridges ASAuthorizationController's delegate callbacks to a single coroutine resume — Apple
 *  guarantees exactly one of the two `authorizationController` overloads fires, once. */
private class AppleSignInDelegate(
    private val continuation: CancellableContinuation<String?>,
    private val onComplete: () -> Unit
) : NSObject(),
    ASAuthorizationControllerDelegateProtocol,
    ASAuthorizationControllerPresentationContextProvidingProtocol {

    override fun authorizationController(
        controller: ASAuthorizationController,
        didCompleteWithAuthorization: ASAuthorization
    ) {
        val credential = didCompleteWithAuthorization.credential as? ASAuthorizationAppleIDCredential
        val tokenData = credential?.identityToken
        val idToken = tokenData?.let { NSString(data = it, encoding = NSUTF8StringEncoding) as String }
        onComplete()
        continuation.resume(idToken)
    }

    override fun authorizationController(controller: ASAuthorizationController, didCompleteWithError: NSError) {
        // ASAuthorizationErrorCanceled == 1001 — the user closed the sheet, not a real failure.
        onComplete()
        if (didCompleteWithError.code == 1001L) {
            continuation.resume(null)
        } else {
            continuation.resumeWithException(RuntimeException(didCompleteWithError.localizedDescription))
        }
    }

    override fun presentationAnchorForAuthorizationController(controller: ASAuthorizationController): ASPresentationAnchor {
        val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
        return windows.firstOrNull { it.keyWindow } ?: windows.firstOrNull() ?: UIWindow()
    }
}

private val nonceCharset = (('0'..'9') + ('A'..'Z') + ('a'..'z') + listOf('-', '.')).toList()

/** The nonce's only job is being unpredictable enough to prevent an intercepted Apple identity
 *  token from being replayed, so — unlike [com.vtempe.ui.util.randomNonce], which is fine with
 *  [kotlin.random.Random] for lower-stakes uses — this one uses the platform CSPRNG. */
private fun secureRandomNonce(length: Int = 32): String = memScoped {
    val bytes = allocArray<ByteVar>(length)
    SecRandomCopyBytes(kSecRandomDefault, length.convert(), bytes)
    (0 until length).map { i -> nonceCharset[(bytes[i].toInt() and 0xFF) % nonceCharset.size] }.joinToString("")
}

// ASAuthorizationController's `delegate`/`presentationContextProvider` properties are `weak`
// (standard Cocoa delegate convention), and nothing else holds the controller itself either —
// so both need an explicit strong reference for the lifetime of the async request, or ARC can
// collect them before Apple's picker calls back. Only one Apple sign-in flow can be in flight at
// a time (the button disables itself while loading), so a single top-level slot is enough.
private var activeAppleSignIn: Pair<ASAuthorizationController, AppleSignInDelegate>? = null

private suspend fun requestAppleIdToken(rawNonce: String): String? = suspendCancellableCoroutine { cont ->
    val provider = ASAuthorizationAppleIDProvider()
    val request = provider.createRequest().apply {
        requestedScopes = listOf(ASAuthorizationScopeFullName, ASAuthorizationScopeEmail)
        nonce = sha256Hex(rawNonce)
    }

    val delegate = AppleSignInDelegate(cont) { activeAppleSignIn = null }
    val controller = ASAuthorizationController(authorizationRequests = listOf(request))
    controller.delegate = delegate
    controller.presentationContextProvider = delegate
    activeAppleSignIn = controller to delegate

    controller.performRequests()
    cont.invokeOnCancellation { activeAppleSignIn = null }
}

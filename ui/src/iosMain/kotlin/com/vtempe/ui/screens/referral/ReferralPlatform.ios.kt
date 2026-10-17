package com.vtempe.ui.screens.referral

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.referral.ReferralRepository
import com.vtempe.ui.presenter.ReferralPresenter
import com.vtempe.ui.presenter.ReferralPresenterDelegate
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow

private class IosReferralPresenter(repository: ReferralRepository) : ReferralPresenter {
    private val job = SupervisorJob()
    private val delegate = ReferralPresenterDelegate(repository, CoroutineScope(Dispatchers.Main + job))
    override val state get() = delegate.state
    override fun refresh() = delegate.refresh()
    override fun requestCode() = delegate.requestCode()
    override fun onCodeInput(value: String) = delegate.onCodeInput(value)
    override fun attach() = delegate.attach()
    fun close() = job.cancel()
}

@Composable
actual fun rememberReferralPresenter(): ReferralPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosReferralPresenter(repository = koin.get())
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

/** The view controller currently on screen: the key window's root, then whatever it presents. */
private fun topViewController(): UIViewController? {
    // Same window lookup as the Apple sign-in anchor (SocialSignInButtons.ios.kt), not the
    // deprecated keyWindow property.
    val windows = UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>()
    var top = (windows.firstOrNull { it.keyWindow } ?: windows.firstOrNull())?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberShareText(): (text: String) -> Unit = remember {
    { text ->
        val host = topViewController()
        if (host != null) {
            val sheet = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
            // On iPad the sheet is a popover, and UIKit throws when a popover has no anchor.
            sheet.popoverPresentationController?.let { popover ->
                popover.sourceView = host.view
                popover.sourceRect = host.view.bounds.useContents { CGRectMake(size.width / 2, size.height / 2, 0.0, 0.0) }
            }
            host.presentViewController(sheet, animated = true, completion = null)
        }
    }
}

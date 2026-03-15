package com.vtempe.ios

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * Точка входа для iOS-приложения.
 * Xcode будет вызывать эту функцию, чтобы получить root UIViewController.
 */
fun MainViewController(): UIViewController {
    initKoinIfNeeded()
    return ComposeUIViewController(
        configure = { enforceStrictPlistSanityCheck = false }
    ) {
        AppIosRoot()
    }
}


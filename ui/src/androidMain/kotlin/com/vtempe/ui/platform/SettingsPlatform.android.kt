package com.vtempe.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vtempe.ui.LocalAppRestart

// In-app restart, not Activity.recreate(): recreate() restores the saved back stack and keeps the
// ViewModels, so after a reset the user landed back on an empty Settings screen spinning forever.
private class AndroidSettingsPlatformActions(private val restart: () -> Unit) : SettingsPlatformActions {
    override fun restartApp() = restart()
}

@Composable
actual fun rememberSettingsPlatformActions(): SettingsPlatformActions {
    val restart = LocalAppRestart.current
    return remember(restart) { AndroidSettingsPlatformActions(restart) }
}

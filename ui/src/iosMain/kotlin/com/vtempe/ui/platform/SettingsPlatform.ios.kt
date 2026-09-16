package com.vtempe.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.vtempe.ui.LocalAppRestart

private class IosSettingsPlatformActions(private val restart: () -> Unit) : SettingsPlatformActions {
    override fun restartApp() = restart()
}

@Composable
actual fun rememberSettingsPlatformActions(): SettingsPlatformActions {
    val restart = LocalAppRestart.current
    return remember(restart) { IosSettingsPlatformActions(restart) }
}

package com.vtempe.ui.screens

import com.vtempe.shared.data.di.KoinProvider

actual fun startupDependencies(): StartupDependencies? {
    val koin = KoinProvider.koin ?: return null
    return StartupDependencies(
        accountSession = { koin.getOrNull() },
        profiles = { koin.get() },
        consent = { koin.getOrNull() },
    )
}

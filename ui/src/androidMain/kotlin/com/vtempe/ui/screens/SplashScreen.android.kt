package com.vtempe.ui.screens

import org.koin.core.context.GlobalContext

actual fun startupDependencies(): StartupDependencies? {
    val koin = GlobalContext.getOrNull() ?: return null
    return StartupDependencies(
        accountSession = { koin.getOrNull() },
        profiles = { koin.get() },
        consent = { koin.getOrNull() },
    )
}

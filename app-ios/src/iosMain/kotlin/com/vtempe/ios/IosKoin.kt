package com.vtempe.ios

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.vtempe.shared.data.di.DI
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.db.AppDatabase
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo

private const val DEFAULT_API_BASE_URL = "https://vtempe-server-eoofh53gda-ew.a.run.app"

fun initKoinIfNeeded(apiBaseUrl: String = resolveApiBaseUrl()) {
    if (KoinProvider.koin != null) return

    val iosModule = module {
        single<SqlDriver> { NativeSqliteDriver(AppDatabase.Schema, "app_v2.db") }
        single { AppDatabase(get()) }
    }

    val appToken = resolveAppToken()

    val koinApp = startKoin {
        modules(
            DI.coreModule(apiBaseUrl = apiBaseUrl, appToken = appToken),
            iosModule
        )
    }
    KoinProvider.koin = koinApp.koin
}

private fun resolveApiBaseUrl(): String {
    val environment = NSProcessInfo.processInfo.environment
    val env = (environment["VTEMPE_API_BASE_URL"] ?: environment["API_BASE_URL"])
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val plist = NSBundle.mainBundle
        .objectForInfoDictionaryKey("API_BASE_URL")
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return env ?: plist ?: DEFAULT_API_BASE_URL
}

/** Resolves the app token from Info.plist (APP_TOKEN key) or env variable.
 *  Set APP_TOKEN in your iOS scheme environment variables or Info.plist for production. */
private fun resolveAppToken(): String? {
    val environment = NSProcessInfo.processInfo.environment
    val env = environment["APP_TOKEN"]
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val plist = NSBundle.mainBundle
        .objectForInfoDictionaryKey("APP_TOKEN")
        ?.toString()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return env ?: plist
}


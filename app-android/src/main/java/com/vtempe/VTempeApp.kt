package com.vtempe

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import com.vtempe.di.AppModule
import com.vtempe.shared.data.di.DI
import com.vtempe.shared.domain.repository.PreferencesRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class VTempeApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val apiBaseUrl = BuildConfig.API_BASE_URL
        val appToken = BuildConfig.APP_TOKEN.takeIf { it.isNotBlank() }
        Log.i(TAG, "Using API base URL (${BuildConfig.BUILD_TYPE}): $apiBaseUrl")
        startKoin {
            androidContext(this@VTempeApp)
            modules(DI.coreModule(apiBaseUrl = apiBaseUrl, appToken = appToken), AppModule.module)
        }
        // Apply persisted app preferences (language/theme)
        val koin = GlobalContext.get()
        val prefs = koin.get<PreferencesRepository>()
        val lang = prefs.getLanguageTag()
        if (lang != null) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
        // Theme follows system until a theme picker is implemented
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    }

    companion object {
        private const val TAG = "VTempeApp"
    }
}




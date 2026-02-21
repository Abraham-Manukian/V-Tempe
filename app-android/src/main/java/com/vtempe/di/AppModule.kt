package com.vtempe.di

import com.vtempe.billing.AndroidPurchasesRepository
import com.vtempe.shared.domain.repository.PurchasesRepository
import com.vtempe.ui.vm.ChatViewModel
import com.vtempe.ui.vm.HomeViewModel
import com.vtempe.ui.vm.NutritionViewModel
import com.vtempe.ui.vm.OnboardingViewModel
import com.vtempe.ui.vm.PaywallViewModel
import com.vtempe.ui.vm.ProgressViewModel
import com.vtempe.ui.vm.SettingsViewModel
import com.vtempe.ui.vm.SleepViewModel
import com.vtempe.ui.vm.WorkoutViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import app.cash.sqldelight.db.SqlDriver
import com.vtempe.shared.db.AppDatabase

object AppModule {
    val module = module {
        // Android-specific DI overrides
        single<PurchasesRepository> { AndroidPurchasesRepository(androidContext()) }

        // SQLDelight database
        single<SqlDriver> { AndroidSqliteDriver(AppDatabase.Schema, androidContext(), "app_v2.db") }
        single { AppDatabase(get()) }

        // ViewModels (Android implementations live in :ui)
        viewModel { OnboardingViewModel(get(), get(), get()) }
        viewModel { HomeViewModel(get(), get()) }
        viewModel { WorkoutViewModel(get(), get(), get()) }
        viewModel { NutritionViewModel(get(), get()) }
        viewModel { SleepViewModel(get(), get()) }
        viewModel { ProgressViewModel(get()) }
        viewModel { PaywallViewModel(get()) }
        viewModel { SettingsViewModel(get(), get(), get()) }
        viewModel { ChatViewModel(get(), get()) }
    }
}


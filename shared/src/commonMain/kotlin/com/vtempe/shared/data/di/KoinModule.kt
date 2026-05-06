package com.vtempe.shared.data.di

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.createHttpClient
import com.vtempe.shared.domain.repository.*
import com.vtempe.shared.data.repo.TrainingRepositoryDb
import com.vtempe.shared.data.repo.NetworkAiTrainerRepository
import com.vtempe.shared.data.repo.NetworkChatRepository
import com.vtempe.shared.data.repo.AiResponseCache
import com.vtempe.shared.data.repo.ExerciseCalibrationSettingsRepository
import com.vtempe.shared.data.repo.NutritionRepositoryDb
import com.vtempe.shared.domain.usecase.*
import com.vtempe.shared.data.repo.ProfileRepositoryDb
import com.vtempe.shared.data.repo.SettingsPreferencesRepository
import com.vtempe.shared.data.repo.WorkoutProgressStore
import com.vtempe.shared.data.stub.StubAdviceRepository
import com.vtempe.shared.data.stub.StubPurchasesRepository
import com.vtempe.shared.data.stub.StubSyncRepository
import com.russhwolf.settings.Settings
import org.koin.core.module.Module
import org.koin.dsl.module

object DI {
    fun coreModule(apiBaseUrl: String): Module = module {
        single { createHttpClient() }
        single { ApiClient(get(), apiBaseUrl) }

        // Settings storage
        single { Settings() }
        single { AiResponseCache(get()) }
        single { WorkoutProgressStore(get(), get()) }
        single<ExerciseCalibrationRepository> { ExerciseCalibrationSettingsRepository(get()) }

        // Repositories
        single<PreferencesRepository> { SettingsPreferencesRepository(get()) }
        single<ProfileRepository> { ProfileRepositoryDb(get()) }
        single<AiTrainerRepository> { NetworkAiTrainerRepository(get(), get(), get(), get()) }
        single<ChatRepository> { NetworkChatRepository(get(), get(), get(), get()) }
        single<TrainingRepository> {
            TrainingRepositoryDb(
                db = get(),
                ai = get(),
                validateSubscription = get(),
                cache = get(),
                progressStore = get()
            )
        }
        single<NutritionRepository> {
            NutritionRepositoryDb(
                db = get(),
                ai = get(),
                validateSubscription = get(),
                preferences = get(),
                cache = get()
            )
        }
        // TODO: replace stubs with real platform implementations when ready
        // PurchasesRepository → AndroidPurchasesRepository / SKProductsStore
        // SyncRepository      → real backend sync
        // AdviceRepository    → NetworkAdviceRepository
        single<AdviceRepository> { StubAdviceRepository() }
        single<PurchasesRepository> { StubPurchasesRepository() }
        single<SyncRepository> { StubSyncRepository() }

        // Use cases
        factory { GenerateTrainingPlan(get(), get()) }
        factory { LogWorkoutSet(get()) }
        factory { GenerateNutritionPlan(get(), get()) }
        factory { BootstrapCoachData(get(), get(), get(), get(), get(), get()) }
        factory { EnsureCoachData(get(), get(), get(), get(), get(), get()) }
        factory { SyncWithBackend(get()) }
        factory { ValidateSubscription(get()) }
        factory { AskAiTrainer(get(), get(), get(), get(), get(), get(), get()) }
    }
}

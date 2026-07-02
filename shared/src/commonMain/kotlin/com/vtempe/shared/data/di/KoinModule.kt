package com.vtempe.shared.data.di

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.createHttpClient
import com.vtempe.shared.domain.repository.*
import com.vtempe.shared.domain.repository.AiModelPreferences
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.UnitPreferences
import com.vtempe.shared.data.repo.TrainingRepositoryDb
import com.vtempe.shared.data.repo.NetworkAiTrainerRepository
import com.vtempe.shared.data.repo.NetworkChatRepository
import com.vtempe.shared.data.repo.AiResponseCache
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.data.repo.ExerciseCalibrationSettingsRepository
import com.vtempe.shared.data.repo.NutritionRepositoryDb
import com.vtempe.shared.domain.usecase.*
import com.vtempe.shared.data.repo.ProfileRepositoryDb
import com.vtempe.shared.data.repo.SettingsPreferencesRepository
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.data.repo.WorkoutProgressStore
import com.vtempe.shared.data.stub.NoOpAnalyticsRepository
import com.vtempe.shared.data.stub.StubAdviceRepository
import com.vtempe.shared.data.stub.StubPurchasesRepository
import com.vtempe.shared.data.stub.StubSyncRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

object DI {
    fun coreModule(apiBaseUrl: String, appToken: String? = null): Module = module {
        single { createHttpClient(appToken) }
        single { ApiClient(get(), apiBaseUrl) }

        // Settings storage
        single { Settings() }
        single { AiResponseCache(get()) }
        single<CoachCacheRepository> { get<AiResponseCache>() }
        single { WorkoutProgressStore(get(), get()) }
        single { SleepStore(get()) }
        single { WeightStore(get()) }
        single { ChatHistoryStore(get()) }
        single<ExerciseCalibrationRepository> { ExerciseCalibrationSettingsRepository(get()) }

        // Repositories
        single<PreferencesRepository> { SettingsPreferencesRepository(get()) }
        single<LanguagePreferences> { get<PreferencesRepository>() }
        single<AiModelPreferences> { get<PreferencesRepository>() }
        single<UnitPreferences> { get<PreferencesRepository>() }
        single<ProfileRepository> { ProfileRepositoryDb(get()) }
        single<AiTrainerRepository> {
            NetworkAiTrainerRepository(
                api = get(),
                languagePrefs = get(),
                aiModelPrefs = get(),
                cache = get(),
                progressStore = get(),
                sleepStore = get(),
                weightStore = get()
            )
        }
        single<ChatRepository> {
            NetworkChatRepository(
                api = get(),
                cache = get(),
                aiModelPrefs = get(),
                progressStore = get(),
                sleepStore = get(),
                weightStore = get()
            )
        }
        single<TrainingRepository> {
            TrainingRepositoryDb(
                db = get(),
                ai = get(),
                cache = get(),
                progressStore = get()
            )
        }
        single<NutritionRepository> {
            NutritionRepositoryDb(
                db = get(),
                ai = get(),
                languagePrefs = get(),
                cache = get()
            )
        }
        // TODO: replace stubs with real platform implementations when ready
        // PurchasesRepository → AndroidPurchasesRepository / SKProductsStore
        // SyncRepository      → real backend sync
        // AdviceRepository    → NetworkAdviceRepository
        // AnalyticsRepository → overridden with FirebaseAnalyticsRepository in Android's
        //                       AppModule.kt; iOS keeps this no-op until Firebase iOS is wired.
        single<AdviceRepository> { StubAdviceRepository() }
        single<PurchasesRepository> { StubPurchasesRepository() }
        single<SyncRepository> { StubSyncRepository() }
        single<AnalyticsRepository> { NoOpAnalyticsRepository() }

        // App-level coroutine scope — lives as long as the process, used for background prefetch.
        // Background prefetch must NOT be tied to any single screen's lifecycle.
        single(named("appScope")) {
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
        }

        // Use cases
        factory { GenerateTrainingPlan(get(), get()) }
        factory { LogWorkoutSet(get()) }
        factory { GenerateNutritionPlan(get(), get()) }
        // single (not factory) — holds the Mutex that prevents parallel bootstrap calls
        single { BootstrapCoachData(get(), get(), get(), get(), get(), get()) }
        factory { EnsureCoachData(get(), get(), get(), get(), get(), get(), get(named("appScope"))) }
        factory { ResetCoachData(get(), get()) }
        factory { SyncWithBackend(get()) }
        factory { ValidateSubscription(get()) }
        factory { MaterializeCoachActions(get(), get(), get(), get()) }
        factory {
            AskAiTrainer(
                profileRepository = get(),
                chatRepository = get(),
                languagePrefs = get(),
                materializeCoachActions = get(),
                coachCache = get(),
                trainingRepository = get(),
                nutritionRepository = get()
            )
        }
    }
}

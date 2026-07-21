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
import com.vtempe.shared.data.repo.NetworkEntitlementRepository
import com.vtempe.shared.data.repo.NetworkSyncRepository
import com.vtempe.shared.data.stub.NoOpAnalyticsRepository
import com.vtempe.shared.data.stub.StubAdviceRepository
import com.vtempe.shared.data.stub.StubAuthRepository
import com.vtempe.shared.data.stub.StubPurchasesRepository
import com.russhwolf.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

object DI {
    fun coreModule(apiBaseUrl: String, appToken: String? = null): Module = module {
        // AuthRepository resolved lazily inside the lambda (not `get()` at module-build time) so
        // this always picks up whichever implementation won — the stub here, or Android's
        // FirebaseAuthRepository override — regardless of module load order.
        single { createHttpClient(appToken) { get<AuthRepository>().idToken() } }
        single { ApiClient(get(), apiBaseUrl) }

        // Settings storage
        single { Settings() }
        single { AiResponseCache(get()) }
        single<CoachCacheRepository> { get<AiResponseCache>() }

        // onLocalChange resolves SyncRepository lazily (only when a store actually has a local
        // change to report), not at module-build time — SyncRepository itself depends on these
        // stores for pull/restore, so an eager get() here would be a constructor cycle. Same
        // pattern as AuthRepository's lazy resolution a few lines below (`get()` only works
        // inside a single{}/factory{} definition lambda, not hoisted to a shared val — hence the
        // near-identical block repeated per store rather than factored out). Runs on appScope:
        // sync is fire-and-forget background work, not something a local save should block on.
        single {
            WorkoutProgressStore(get(), get(), onLocalChange = { domain ->
                get<CoroutineScope>(named("appScope")).launch { get<SyncRepository>().pushDomain(domain) }
            })
        }
        single {
            SleepStore(get(), onLocalChange = { domain ->
                get<CoroutineScope>(named("appScope")).launch { get<SyncRepository>().pushDomain(domain) }
            })
        }
        single {
            WeightStore(get(), onLocalChange = { domain ->
                get<CoroutineScope>(named("appScope")).launch { get<SyncRepository>().pushDomain(domain) }
            })
        }
        single { ChatHistoryStore(get()) }
        single<ExerciseCalibrationRepository> { ExerciseCalibrationSettingsRepository(get()) }

        // Repositories
        single<PreferencesRepository> { SettingsPreferencesRepository(get()) }
        single<LanguagePreferences> { get<PreferencesRepository>() }
        single<AiModelPreferences> { get<PreferencesRepository>() }
        single<UnitPreferences> { get<PreferencesRepository>() }
        // Bound under its concrete type (not just the ProfileRepository interface below) because
        // NetworkSyncRepository needs restoreProfile(), which isn't part of that interface —
        // only the sync system should be able to write a profile without triggering a push.
        single {
            ProfileRepositoryDb(get(), onLocalChange = { domain ->
                get<CoroutineScope>(named("appScope")).launch { get<SyncRepository>().pushDomain(domain) }
            })
        }
        single<ProfileRepository> { get<ProfileRepositoryDb>() }
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
        // AdviceRepository    → NetworkAdviceRepository
        // AnalyticsRepository → overridden with FirebaseAnalyticsRepository in Android's
        //                       AppModule.kt; iOS keeps this no-op until Firebase iOS is wired.
        // AuthRepository      → overridden with FirebaseAuthRepository in Android's
        //                       AppModule.kt; iOS keeps this stub until Firebase iOS is wired.
        single<AdviceRepository> { StubAdviceRepository() }
        single<PurchasesRepository> { StubPurchasesRepository() }
        // Cross-platform on both Android and iOS — sync is plain HTTP through ApiClient's
        // existing bearer-token auth, no Firebase SDK dependency like Auth/Analytics have.
        // Requests made while signed out simply go unauthenticated and the server 401s, which
        // pushDomain/pullAll already treat as a swallowed failure.
        single<SyncRepository> { NetworkSyncRepository(get(), get(), get(), get(), get()) }
        single<AnalyticsRepository> { NoOpAnalyticsRepository() }
        single<AuthRepository> { StubAuthRepository() }
        single<EntitlementRepository> { NetworkEntitlementRepository(get()) }

        // Google OAuth "web" client id, needed by Credential Manager's GetGoogleIdOption on
        // Android. Empty here; overridden with the real value (read from google-services.json
        // via BuildConfig) in Android's AppModule.kt. iOS doesn't use this — it authenticates
        // with Apple, not Google.
        single(named("googleWebClientId")) { "" }

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
        factory { ValidateSubscription(get()) }
        factory { SyncAnalyticsProfile(get(), get()) }
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

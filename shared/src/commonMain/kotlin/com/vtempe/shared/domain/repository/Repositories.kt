package com.vtempe.shared.domain.repository

import com.vtempe.shared.domain.account.AccountDataFailure
import com.vtempe.shared.domain.model.*
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface ProfileRepository {
    suspend fun getProfile(): Profile?
    suspend fun upsertProfile(profile: Profile)
    suspend fun clearAll()
}

interface TrainingRepository {
    suspend fun generatePlan(profile: Profile, weekIndex: Int): TrainingPlan
    suspend fun logSet(workoutId: String, set: WorkoutSet)
    /** All workouts — for history / progress screens. */
    fun observeWorkouts(): Flow<List<Workout>>
    /** Only workouts for the given week — for the active workout screen. */
    fun observeWorkoutsByWeek(weekIndex: Int): Flow<List<Workout>>
    fun observeWorkoutProgress(): Flow<Map<String, WorkoutProgress>>
    suspend fun saveWorkoutProgress(progress: WorkoutProgress)
    /** Atomically applies [transform] to the stored progress of [workoutId] (read-modify-write). */
    suspend fun updateWorkoutProgress(workoutId: String, transform: (WorkoutProgress) -> WorkoutProgress)
    suspend fun recentWorkoutSummaries(limit: Int = 6): List<WorkoutSummary>
    suspend fun savePlan(plan: TrainingPlan)
    suspend fun hasPlan(weekIndex: Int): Boolean
    /** Deletes plan workouts for all weeks >= weekIndex. Called on force-refresh (profile change). */
    suspend fun deleteWeeksFrom(weekIndex: Int)
}

interface NutritionRepository {
    suspend fun generatePlan(profile: Profile, weekIndex: Int): NutritionPlan
    suspend fun savePlan(plan: NutritionPlan)
    fun observePlan(): Flow<NutritionPlan?>
    /** Pure DB check — no side effects on the observable flow. */
    suspend fun hasPlan(weekIndex: Int): Boolean
    /**
     * Marks [weekIndex] as the week currently on screen.
     * Loads that week's plan from DB into the observable flow so the UI renders immediately.
     * Returns true if a cached plan was found.
     */
    suspend fun setActiveWeek(weekIndex: Int): Boolean
    /**
     * Registers the active week WITHOUT loading from DB (no IO, instant).
     * Call this before a force-refresh so [savePlan] knows to update the flow
     * once the new AI plan arrives, but without showing stale cached data first.
     */
    fun registerActiveWeek(weekIndex: Int)
    /** Deletes meal plans for all weeks >= weekIndex. Called on force-refresh. */
    suspend fun deleteWeeksFrom(weekIndex: Int)
}

interface AdviceRepository {
    suspend fun getAdvice(profile: Profile, context: Map<String, Any?>): Advice
    suspend fun saveAdvice(topic: String, advice: Advice)
    fun observeAdvice(topic: String): Flow<Advice>
    suspend fun hasAdvice(topic: String): Boolean
}

/** Store-side billing (Play Billing; the purchase flow is VT-AUD-054). Not the subscription
 *  status shown to the user: that is the server's entitlement, via CheckSubscription. */
interface PurchasesRepository {
    suspend fun isSubscriptionActive(): Boolean
}

/** The signed-in account's subscription as the server decided it ([expiresAt]: ISO-8601). */
data class SubscriptionEntitlement(val active: Boolean, val expiresAt: String?)

/** `GET /me/entitlement`; the wire shape stays in the data layer. */
interface EntitlementRepository {
    suspend fun fetchEntitlement(): DataResult<SubscriptionEntitlement>
}

/** The last subscription status the server confirmed, per account, for offline display. */
interface LastKnownEntitlement {
    fun save(uid: String, entitlement: SubscriptionEntitlement)

    /** Only ever [uid]'s own entry — another account's status is never returned. */
    fun load(uid: String): SubscriptionEntitlement?
}

/** The weigh-in history ([date]: ISO-8601 day). */
interface BodyWeightLog {
    fun logWeight(date: String, kg: Double)
}

/** Keep in sync with the server's ALLOWED_SYNC_DOMAINS and MERGEABLE_SYNC_DOMAINS
 *  (server/.../features/sync/data/service/SyncService.kt). [diary]: entries keyed by date or
 *  workout, merged with other devices' entries instead of replaced (VT-AUD-057). */
enum class SyncDomain(val wireKey: String, val diary: Boolean) {
    PROFILE("profile", diary = false),
    WORKOUT_PROGRESS("workoutProgress", diary = true),
    SLEEP("sleep", diary = true),
    SLEEP_NOTES("sleepNotes", diary = true),
    WEIGHT("weight", diary = true)
}

/**
 * Cross-device backup of local progress (profile, workout completions, sleep, weight) — separate
 * from [EntitlementRepository] (subscription status, already server-side by uid) and from
 * [TrainingRepository]/[NutritionRepository] (AI-generated plans, regenerable, not synced).
 *
 * Each domain is pushed as an opaque JSON snapshot of its entire current local state — last
 * write wins, no field-level merge. [WorkoutProgressStore][com.vtempe.shared.data.repo.WorkoutProgressStore],
 * [SleepStore][com.vtempe.shared.data.repo.SleepStore] and [WeightStore][com.vtempe.shared.data.repo.WeightStore]
 * already persist their state as one JSON blob locally, so their push is literally that blob;
 * [ProfileRepository] is serialized from [com.vtempe.shared.domain.model.Profile] instead, since
 * it isn't Settings-backed.
 */
interface SyncRepository {
    /** Pushes the current local snapshot of [domain] to the server. Fire-and-forget: call after
     *  every local write. Does nothing unless the device data belongs to the signed-in account
     *  ([com.vtempe.shared.domain.account.LocalDataOwnerStore]). Failures are swallowed — the next
     *  successful push naturally carries the latest state. */
    suspend fun pushDomain(domain: SyncDomain)

    /** Uploads every domain with local changes the server has not confirmed yet — e.g. ones that
     *  failed offline, even across a restart (VT-AUD-056). Same owner rule as [pushDomain]. */
    suspend fun pushPending()

    /** Everything the server holds for [uid], or why it couldn't be fetched (offline, [uid] no
     *  longer signed in, refused, server error). */
    suspend fun fetchRemote(uid: String): RemoteFetch

    /** Writes [snapshot] into local storage. Domains missing from it are left untouched, so
     *  callers that switch accounts clear local data first. All or nothing: when any payload can't
     *  be read, nothing is written and the unreadable domains are returned (VT-AUD-049/060). */
    suspend fun restore(snapshot: RemoteSyncSnapshot): Set<SyncDomain>

    /** Domains of [snapshot] that [restore] could not read — checked before local data is cleared. */
    fun unreadableDomains(snapshot: RemoteSyncSnapshot): Set<SyncDomain>

    /** Returns once no upload or restore is running. Uploads that start later re-check the owner,
     *  so after clearing it nothing more reaches the server from this device (VT-AUD-059). */
    suspend fun awaitIdle()

    /** Replaces every domain of [uid] with the device's; domains the device has nothing for are
     *  stored as [RemoteSyncSnapshot.EMPTY_PAYLOAD], so the account ends up mirroring the device
     *  (a reset, or the device's data chosen over the account's). Returns false if any upload failed. */
    suspend fun pushAll(uid: String): Boolean

    /** Delivers everything the device changed to [uid] — diaries merged with what other devices
     *  added (VT-AUD-057), not replaced — so the device's data is safe on the server, e.g. before
     *  signing out. Returns false if anything could not be delivered. */
    suspend fun deliverAll(uid: String): Boolean

    /** True when the device holds changes the server has not confirmed (offline, or held back
     *  without the health-data consent): wiping the device now would lose them for good. */
    fun hasUndeliveredChanges(): Boolean
}

sealed interface RemoteFetch {
    data class Fetched(val snapshot: RemoteSyncSnapshot) : RemoteFetch
    data class Failed(val reason: AccountDataFailure) : RemoteFetch
}

/** Server-side sync blobs keyed by [SyncDomain.wireKey]. */
data class RemoteSyncSnapshot(val payloads: Map<String, String>) {
    /** True when the account holds no progress — never synced, or wiped by a reset. */
    val isEmpty: Boolean get() = payloads.values.all { it == EMPTY_PAYLOAD }

    companion object {
        /** Map-shaped domains decode it as empty; for the profile it means "no profile". */
        const val EMPTY_PAYLOAD = "{}"
    }
}

/**
 * Minimal analytics/crash-reporting facade so feature code never depends on Firebase
 * directly. Platforms without a wired backend (iOS, or Android builds without
 * google-services.json) get a no-op implementation — calling these methods is always safe.
 */
interface AnalyticsRepository {
    fun logEvent(name: String, params: Map<String, String> = emptyMap())
    fun setUserProperty(key: String, value: String?)
    fun recordNonFatal(throwable: Throwable, message: String? = null)

    /** Turns the backend's own automatic usage collection on/off (e.g. Firebase's session and
     *  first_open events), independent of explicit [logEvent] calls. No-op by default. */
    fun setCollectionEnabled(enabled: Boolean) = Unit
}

/** Named analytics events tracked across the app — keep this the single source of truth. */
object AnalyticsEvents {
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val PLAN_GENERATED = "plan_generated"
    const val CHAT_MESSAGE_SENT = "chat_message_sent"
    const val WORKOUT_COMPLETED = "workout_completed"
    const val PAYWALL_SHOWN = "paywall_shown"
    const val SUBSCRIPTION_PURCHASED = "subscription_purchased"
}

/** Narrow interface for classes that only need locale access. */
interface LanguagePreferences {
    fun getLanguageTag(): String?
    fun setLanguageTag(tag: String?)
}

/** Narrow interface for classes that only need AI model selection. */
interface AiModelPreferences {
    fun getAiModelMode(): AiModelMode
    fun setAiModelMode(mode: AiModelMode)
}

/** Narrow interface for classes that only need measurement units. */
interface UnitPreferences {
    fun getUnits(): String?
    fun setUnits(units: String?)
}

/**
 * Opt-in consent for sending demographic analytics (age/height/weight/budget buckets,
 * chosen trainer) to Firebase. Defaults to false — must be explicit opt-in, never opt-out,
 * per GDPR and Google Play / App Store data-collection requirements.
 */
interface AnalyticsConsentPreferences {
    fun getAnalyticsConsent(): Boolean
    fun setAnalyticsConsent(granted: Boolean)
}

/** Combined interface used by Settings and Onboarding screens. */
interface PreferencesRepository :
    LanguagePreferences,
    AiModelPreferences,
    UnitPreferences,
    AnalyticsConsentPreferences,
    com.vtempe.shared.domain.consent.HealthDataConsentPreferences

interface ExerciseCalibrationRepository {
    suspend fun get(exerciseId: String): com.vtempe.shared.domain.exercise.ExerciseCalibrationRecord?
    suspend fun list(): List<com.vtempe.shared.domain.exercise.ExerciseCalibrationRecord>
    suspend fun upsert(record: com.vtempe.shared.domain.exercise.ExerciseCalibrationRecord)
    suspend fun clear(exerciseId: String)
    suspend fun clearAll()
}

interface AiTrainerRepository {
    suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan>
    suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan>
    suspend fun getSleepAdvice(profile: Profile): DataResult<Advice>
    suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle>
}

data class ChatMessage(val role: String, val content: String)

data class CoachBundle(
    val trainingPlan: TrainingPlan? = null,
    val nutritionPlan: NutritionPlan? = null,
    val sleepAdvice: Advice? = null
)

data class CoachResponse(
    val reply: String,
    val trainingPlan: TrainingPlan? = null,
    val nutritionPlan: NutritionPlan? = null,
    val sleepAdvice: Advice? = null,
    val actions: List<CoachAction> = emptyList()
)

interface ChatRepository {
    suspend fun send(
        profile: Profile,
        history: List<ChatMessage>,
        userMessage: String,
        locale: String?,
        currentTrainingPlan: TrainingPlan? = null,
        currentNutritionPlan: NutritionPlan? = null
    ): DataResult<CoachResponse>
}

/**
 * Domain-layer port for tracking the freshness of AI-generated coach data.
 * Implemented by the data layer; domain use cases depend only on this interface.
 */
interface CoachCacheRepository {
    fun bundleVersion(): Int?
    fun bundleTimestampMillis(): Long?
    fun markBundleFresh(version: Int, timestampMillis: Long)
    fun clearAll()

    /** Epoch = timestamp of the very first successful bootstrap. Never changes after set. */
    fun planEpochDateMs(): Long?
    fun setPlanEpochDate(ms: Long)

    /**
     * Clears the epoch date (and all other cache).
     * Call ONLY on full reset / re-registration so the week counter restarts from zero.
     * Regular cache clears (schema upgrades etc.) should NOT reset the epoch.
     */
    fun clearAllAndResetEpoch()
}








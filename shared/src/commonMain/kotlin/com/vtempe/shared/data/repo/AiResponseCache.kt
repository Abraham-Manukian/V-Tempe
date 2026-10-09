package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.dto.AiBootstrapResponseDto
import com.vtempe.shared.data.network.dto.NutritionPlanDto
import com.vtempe.shared.data.network.dto.TrainingPlanDto
import com.vtempe.shared.data.network.dto.AdviceDto
import com.vtempe.shared.data.network.dto.ChatResponse
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.russhwolf.settings.Settings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private const val KEY_TRAINING = "cache_training_plan"
private const val KEY_NUTRITION = "cache_nutrition_plan"
private const val KEY_ADVICE = "cache_sleep_advice"
private const val KEY_BUNDLE = "cache_bootstrap_bundle"
private const val KEY_CHAT = "cache_chat_response"
private const val KEY_BUNDLE_VERSION = "cache_bundle_version"
private const val KEY_BUNDLE_TIMESTAMP = "cache_bundle_timestamp"
private const val KEY_PLAN_EPOCH = "coach_plan_epoch_ms"
private const val KEY_SUFFIX = ".request_key"

class AiResponseCache(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) : CoachCacheRepository {
    // Every cached response is stored with the key of the request it answered (VT-AUD-021):
    // week, locale and profile. An offline fallback is served only for the SAME key, so a
    // network failure for a new week or after a profile change (new allergy, new equipment)
    // can never return an old week's or old profile's plan as a success. A plan saved locally
    // for another week than the cached request is stored without a key and never served.

    fun storeTraining(dto: TrainingPlanDto, key: String? = null) =
        store(KEY_TRAINING, json.encodeToString(dto), key ?: sameWeekKey(KEY_TRAINING, dto.weekIndex))

    fun lastTraining(key: String): TrainingPlanDto? =
        load(KEY_TRAINING, key)?.let { runCatching { json.decodeFromString<TrainingPlanDto>(it) }.getOrNull() }

    fun storeNutrition(dto: NutritionPlanDto, key: String? = null) =
        store(KEY_NUTRITION, json.encodeToString(dto), key ?: sameWeekKey(KEY_NUTRITION, dto.weekIndex))

    fun lastNutrition(key: String): NutritionPlanDto? =
        load(KEY_NUTRITION, key)?.let { runCatching { json.decodeFromString<NutritionPlanDto>(it) }.getOrNull() }

    fun storeAdvice(dto: AdviceDto, key: String? = null) = store(KEY_ADVICE, json.encodeToString(dto), key)

    fun lastAdvice(key: String): AdviceDto? =
        load(KEY_ADVICE, key)?.let { runCatching { json.decodeFromString<AdviceDto>(it) }.getOrNull() }

    fun storeBundle(dto: AiBootstrapResponseDto, key: String? = null) = store(KEY_BUNDLE, json.encodeToString(dto), key)

    fun lastBundle(key: String): AiBootstrapResponseDto? =
        load(KEY_BUNDLE, key)?.let { runCatching { json.decodeFromString<AiBootstrapResponseDto>(it) }.getOrNull() }

    private fun store(slot: String, payload: String, key: String?) {
        settings.putString(slot, payload)
        if (key != null) settings.putString(slot + KEY_SUFFIX, key) else settings.remove(slot + KEY_SUFFIX)
    }

    /** A plan saved locally (chat edit, bootstrap save) for the week the cached response was
     *  for keeps that response's key: a local save changes the plan, not the profile. */
    private fun sameWeekKey(slot: String, weekIndex: Int): String? =
        settings.getStringOrNull(slot + KEY_SUFFIX)?.takeIf { it.startsWith("$weekIndex|") }

    private fun load(slot: String, key: String): String? =
        settings.getStringOrNull(slot)?.takeIf { settings.getStringOrNull(slot + KEY_SUFFIX) == key }

    override fun bundleVersion(): Int? = settings.getIntOrNull(KEY_BUNDLE_VERSION)

    override fun bundleTimestampMillis(): Long? = settings.getLongOrNull(KEY_BUNDLE_TIMESTAMP)

    override fun markBundleFresh(version: Int, timestampMillis: Long) {
        settings.putInt(KEY_BUNDLE_VERSION, version)
        settings.putLong(KEY_BUNDLE_TIMESTAMP, timestampMillis)
    }

    override fun planEpochDateMs(): Long? = settings.getLongOrNull(KEY_PLAN_EPOCH)

    override fun setPlanEpochDate(ms: Long) {
        settings.putLong(KEY_PLAN_EPOCH, ms)
    }

    fun clearBundleMetadata() {
        settings.remove(KEY_BUNDLE_VERSION)
        settings.remove(KEY_BUNDLE_TIMESTAMP)
        // Note: KEY_PLAN_EPOCH is intentionally NOT cleared — epoch date is permanent
    }

    override fun clearAll() {
        listOf(KEY_TRAINING, KEY_NUTRITION, KEY_ADVICE, KEY_BUNDLE).forEach {
            settings.remove(it)
            settings.remove(it + KEY_SUFFIX)
        }
        settings.remove(KEY_CHAT)
        clearBundleMetadata()
        // KEY_PLAN_EPOCH intentionally kept — week count must survive cache clears
    }

    /** Full wipe including epoch. Use ONLY on re-registration / account reset. */
    override fun clearAllAndResetEpoch() {
        clearAll()
        settings.remove(KEY_PLAN_EPOCH)
    }

    fun storeChatResponse(dto: ChatResponse) {
        settings.putString(KEY_CHAT, json.encodeToString(dto))
    }

    fun lastChatResponse(): ChatResponse? =
        settings.getStringOrNull(KEY_CHAT)?.let { runCatching { json.decodeFromString<ChatResponse>(it) }.getOrNull() }
}


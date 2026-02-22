package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.dto.AiBootstrapResponseDto
import com.vtempe.shared.data.network.dto.NutritionPlanDto
import com.vtempe.shared.data.network.dto.TrainingPlanDto
import com.vtempe.shared.data.network.dto.AdviceDto
import com.vtempe.shared.data.network.dto.ChatResponse
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

class AiResponseCache(
    private val settings: Settings,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    fun storeTraining(dto: TrainingPlanDto) {
        settings.putString(KEY_TRAINING, json.encodeToString(dto))
    }

    fun lastTraining(): TrainingPlanDto? =
        settings.getStringOrNull(KEY_TRAINING)?.let { runCatching { json.decodeFromString<TrainingPlanDto>(it) }.getOrNull() }

    fun storeNutrition(dto: NutritionPlanDto) {
        settings.putString(KEY_NUTRITION, json.encodeToString(dto))
    }

    fun lastNutrition(): NutritionPlanDto? =
        settings.getStringOrNull(KEY_NUTRITION)?.let { runCatching { json.decodeFromString<NutritionPlanDto>(it) }.getOrNull() }

    fun storeAdvice(dto: AdviceDto) {
        settings.putString(KEY_ADVICE, json.encodeToString(dto))
    }

    fun lastAdvice(): AdviceDto? =
        settings.getStringOrNull(KEY_ADVICE)?.let { runCatching { json.decodeFromString<AdviceDto>(it) }.getOrNull() }

    fun storeBundle(dto: AiBootstrapResponseDto) {
        settings.putString(KEY_BUNDLE, json.encodeToString(dto))
    }

    fun lastBundle(): AiBootstrapResponseDto? =
        settings.getStringOrNull(KEY_BUNDLE)?.let { runCatching { json.decodeFromString<AiBootstrapResponseDto>(it) }.getOrNull() }

    fun bundleVersion(): Int? = settings.getIntOrNull(KEY_BUNDLE_VERSION)

    fun bundleTimestampMillis(): Long? = settings.getLongOrNull(KEY_BUNDLE_TIMESTAMP)

    fun markBundleFresh(version: Int, timestampMillis: Long) {
        settings.putInt(KEY_BUNDLE_VERSION, version)
        settings.putLong(KEY_BUNDLE_TIMESTAMP, timestampMillis)
    }

    fun clearBundleMetadata() {
        settings.remove(KEY_BUNDLE_VERSION)
        settings.remove(KEY_BUNDLE_TIMESTAMP)
    }

    fun clearAll() {
        settings.remove(KEY_TRAINING)
        settings.remove(KEY_NUTRITION)
        settings.remove(KEY_ADVICE)
        settings.remove(KEY_BUNDLE)
        settings.remove(KEY_CHAT)
        clearBundleMetadata()
    }

    fun storeChatResponse(dto: ChatResponse) {
        settings.putString(KEY_CHAT, json.encodeToString(dto))
    }

    fun lastChatResponse(): ChatResponse? =
        settings.getStringOrNull(KEY_CHAT)?.let { runCatching { json.decodeFromString<ChatResponse>(it) }.getOrNull() }
}


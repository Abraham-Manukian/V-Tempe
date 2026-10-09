package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.AdviceDto
import com.vtempe.shared.data.network.dto.AiAdviceRequestDto
import com.vtempe.shared.data.network.dto.AiBootstrapRequestDto
import com.vtempe.shared.data.network.dto.AiBootstrapResponseDto
import com.vtempe.shared.data.network.dto.AiNutritionRequestDto
import com.vtempe.shared.data.network.dto.AiTrainingRequestDto
import com.vtempe.shared.data.network.dto.NutritionPlanDto
import com.vtempe.shared.data.network.dto.TrainingPlanDto
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AiModelPreferences
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.serialization.json.Json

private val keyJson = Json { encodeDefaults = true }

class NetworkAiTrainerRepository(
    private val api: ApiClient,
    private val languagePrefs: LanguagePreferences,
    private val aiModelPrefs: AiModelPreferences,
    private val cache: AiResponseCache,
    private val progressStore: WorkoutProgressStore,
    private val sleepStore: SleepStore,
    private val weightStore: WeightStore,
) : AiTrainerRepository {

    private fun currentLocale(): String? = languagePrefs.getLanguageTag()?.takeIf { it.isNotBlank() }
    private fun currentLlmMode() = aiModelPrefs.getAiModelMode()

    /** Identity of a request for the offline cache: same week (if any), locale and profile. */
    private fun cacheKey(profile: Profile, weekIndex: Int?): String =
        "${weekIndex ?: "-"}|${currentLocale().orEmpty()}|${keyJson.encodeToString(Profile.serializer(), profile)}"

    override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> {
        val request = AiTrainingRequestDto.fromDomain(
            profile = profile,
            weekIndex = weekIndex,
            locale = currentLocale(),
            llmMode = currentLlmMode(),
            recentWorkouts = progressStore.recentSummaries(),
            sleepHistory = sleepStore.recentEntries(),
            recentWeights = weightStore.recentEntries()
        )
        return when (val result = api.postResult<AiTrainingRequestDto, TrainingPlanDto>("/ai/training", request)) {
            is DataResult.Success -> {
                val domain = result.data.toDomain()
                cache.storeTraining(result.data, cacheKey(profile, weekIndex))
                DataResult.Success(domain, fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastTraining(cacheKey(profile, weekIndex))?.let { cached ->
                    Napier.w("AI training plan request failed, using cached version", result.throwable)
                    return DataResult.Success(cached.toDomain(), fromCache = true, rawPayload = result.rawPayload)
                }
                result
            }
        }
    }

    override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> {
        val request = AiNutritionRequestDto.fromDomain(
            profile = profile,
            weekIndex = weekIndex,
            locale = currentLocale(),
            llmMode = currentLlmMode(),
            recentWorkouts = progressStore.recentSummaries(),
            sleepHistory = sleepStore.recentEntries(),
            recentWeights = weightStore.recentEntries()
        )
        return when (val result = api.postResult<AiNutritionRequestDto, NutritionPlanDto>("/ai/nutrition", request)) {
            is DataResult.Success -> {
                val domain = result.data.toDomain()
                cache.storeNutrition(result.data, cacheKey(profile, weekIndex))
                DataResult.Success(domain, fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastNutrition(cacheKey(profile, weekIndex))?.let { cached ->
                    Napier.w("AI nutrition plan request failed, using cached version", result.throwable)
                    return DataResult.Success(cached.toDomain(), fromCache = true, rawPayload = result.rawPayload)
                }
                result
            }
        }
    }

    override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> {
        val request = AiAdviceRequestDto.fromDomain(
            profile = profile,
            locale = currentLocale(),
            llmMode = currentLlmMode(),
            recentWorkouts = progressStore.recentSummaries(),
            sleepHistory = sleepStore.recentEntries(),
            recentWeights = weightStore.recentEntries()
        )
        return when (val result = api.postResult<AiAdviceRequestDto, AdviceDto>("/ai/sleep", request)) {
            is DataResult.Success -> {
                val domain = result.data.toDomain()
                cache.storeAdvice(result.data, cacheKey(profile, null))
                DataResult.Success(domain, fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastAdvice(cacheKey(profile, null))?.let { cached ->
                    Napier.w("AI advice request failed, using cached version", result.throwable)
                    return DataResult.Success(cached.toDomain(), fromCache = true, rawPayload = result.rawPayload)
                }
                result
            }
        }
    }

    override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> {
        val request = AiBootstrapRequestDto.fromDomain(
            profile = profile,
            weekIndex = weekIndex,
            locale = currentLocale(),
            llmMode = currentLlmMode(),
            recentWorkouts = progressStore.recentSummaries(),
            sleepHistory = sleepStore.recentEntries(),
            recentWeights = weightStore.recentEntries()
        )
        val result = api.postResult<AiBootstrapRequestDto, AiBootstrapResponseDto>("/ai/bootstrap", request)
        return when (result) {
            is DataResult.Success -> {
                val key = cacheKey(profile, weekIndex)
                cache.storeBundle(result.data, key)
                result.data.trainingPlan?.let { cache.storeTraining(it, key) }
                result.data.nutritionPlan?.let { cache.storeNutrition(it, key) }
                result.data.sleepAdvice?.let { cache.storeAdvice(it, cacheKey(profile, null)) }
                DataResult.Success(result.data.toDomain(), fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastBundle(cacheKey(profile, weekIndex))?.let { cached ->
                    Napier.w("Bootstrap request failed, using cached bundle", result.throwable)
                    return DataResult.Success(cached.toDomain(), fromCache = true, rawPayload = result.rawPayload)
                }
                result
            }
        }
    }
}


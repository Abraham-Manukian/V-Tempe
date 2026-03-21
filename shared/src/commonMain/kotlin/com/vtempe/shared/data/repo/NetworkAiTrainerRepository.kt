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
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier

class NetworkAiTrainerRepository(
    private val api: ApiClient,
    private val preferences: PreferencesRepository,
    private val cache: AiResponseCache,
    private val progressStore: WorkoutProgressStore
) : AiTrainerRepository {

    private fun currentLocale(): String? = preferences.getLanguageTag()?.takeIf { it.isNotBlank() }
    private fun currentLlmMode() = preferences.getAiModelMode()

    override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> {
        val request = AiTrainingRequestDto.fromDomain(
            profile = profile,
            weekIndex = weekIndex,
            locale = currentLocale(),
            llmMode = currentLlmMode(),
            recentWorkouts = progressStore.recentSummaries()
        )
        return when (val result = api.postResult<AiTrainingRequestDto, TrainingPlanDto>("/ai/training", request)) {
            is DataResult.Success -> {
                val domain = result.data.toDomain()
                cache.storeTraining(result.data)
                DataResult.Success(domain, fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastTraining()?.let { cached ->
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
            recentWorkouts = progressStore.recentSummaries()
        )
        return when (val result = api.postResult<AiNutritionRequestDto, NutritionPlanDto>("/ai/nutrition", request)) {
            is DataResult.Success -> {
                val domain = result.data.toDomain()
                cache.storeNutrition(result.data)
                DataResult.Success(domain, fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastNutrition()?.let { cached ->
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
            recentWorkouts = progressStore.recentSummaries()
        )
        return when (val result = api.postResult<AiAdviceRequestDto, AdviceDto>("/ai/sleep", request)) {
            is DataResult.Success -> {
                val domain = result.data.toDomain()
                cache.storeAdvice(result.data)
                DataResult.Success(domain, fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastAdvice()?.let { cached ->
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
            recentWorkouts = progressStore.recentSummaries()
        )
        val result = api.postResult<AiBootstrapRequestDto, AiBootstrapResponseDto>("/ai/bootstrap", request)
        return when (result) {
            is DataResult.Success -> {
                cache.storeBundle(result.data)
                result.data.trainingPlan?.let { cache.storeTraining(it) }
                result.data.nutritionPlan?.let { cache.storeNutrition(it) }
                result.data.sleepAdvice?.let { cache.storeAdvice(it) }
                DataResult.Success(result.data.toDomain(), fromCache = result.fromCache, rawPayload = result.rawPayload)
            }
            is DataResult.Failure -> {
                cache.lastBundle()?.let { cached ->
                    Napier.w("Bootstrap request failed, using cached bundle", result.throwable)
                    return DataResult.Success(cached.toDomain(), fromCache = true, rawPayload = result.rawPayload)
                }
                result
            }
        }
    }
}


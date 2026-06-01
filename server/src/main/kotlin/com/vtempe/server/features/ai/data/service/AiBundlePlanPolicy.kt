package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import java.util.Locale

internal fun normalizeBundle(
    bundle: AiBootstrapResponse,
    locale: Locale,
    profile: AiProfile? = null,
    trainingPlanResolver: TrainingPlanResolver = builtInTrainingPlanResolver
): AiBootstrapResponse =
    AiBootstrapResponse(
        trainingPlan = bundle.trainingPlan?.let { normalizeTrainingPlan(it, profile, trainingPlanResolver) },
        nutritionPlan = bundle.nutritionPlan?.let { normalizeNutritionPlan(it, locale, profile) },
        sleepAdvice = bundle.sleepAdvice?.let { normalizeAdvice(it) }
    )

internal fun normalizeBundle(
    bundle: AiBootstrapResponse,
    trainingPlanResolver: TrainingPlanResolver = builtInTrainingPlanResolver
): AiBootstrapResponse =
    normalizeBundle(bundle, Locale.ENGLISH, trainingPlanResolver = trainingPlanResolver)

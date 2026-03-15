package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.bootstrap.AiBootstrapResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import java.util.Locale

internal fun normalizeBundle(
    bundle: AiBootstrapResponse,
    locale: Locale,
    profile: AiProfile? = null
): AiBootstrapResponse =
    AiBootstrapResponse(
        trainingPlan = bundle.trainingPlan?.let { normalizeTrainingPlan(it) },
        nutritionPlan = bundle.nutritionPlan?.let { normalizeNutritionPlan(it, locale, profile) },
        sleepAdvice = bundle.sleepAdvice?.let { normalizeAdvice(it) }
    )

internal fun normalizeBundle(bundle: AiBootstrapResponse): AiBootstrapResponse =
    normalizeBundle(bundle, Locale.ENGLISH)

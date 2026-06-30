package com.vtempe.server.features.ai.data.service.nutrition

import com.vtempe.server.features.ai.data.service.NutritionTargets
import com.vtempe.server.features.ai.data.service.computeTargetNutrition
import com.vtempe.server.shared.dto.profile.AiProfile

/**
 * Public, testable facade over the calorie/macro target computation that lives in
 * [computeTargetNutrition] (CoachBundlePromptBuilder.kt). Centralizes the Mifflin-St Jeor
 * BMR → TDEE → goal-adjusted target pipeline so callers and unit tests have one entry point.
 *
 * Kept as a thin delegate (no duplicated math) so the production prompt path and tests
 * always exercise the exact same implementation.
 */
internal object NutritionTargetCalculator {

    fun targetsFor(profile: AiProfile): NutritionTargets = computeTargetNutrition(profile)
}

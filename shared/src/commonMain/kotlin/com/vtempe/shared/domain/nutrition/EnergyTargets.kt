package com.vtempe.shared.domain.nutrition

import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.clampedForCalculations
import kotlin.math.max

/**
 * Daily calorie and macro targets — client mirror of the server's `computeTargetNutrition`
 * (CoachBundlePromptBuilder.kt): Mifflin-St Jeor BMR, lifestyle/training activity factor, goal
 * adjustment and evidence-based protein/fat ranges (Morton 2018, ISSN 2017). The server's
 * recent-weight trend adjustment is not available offline. Both sides are pinned by
 * `testdata/nutrition-target-cases.tsv`.
 *
 * The macros always fit the calorie target (VT-AUD-029): protein and fat were computed first and
 * carbs clipped at 0, so 4P + 9F could exceed the target. Now the target is binding — fat gives
 * way first, but not below 20% of calories (the lower bound of the accepted fat range), then
 * protein — and carbs take the remainder, so 4P + 9F + 4C equals the target within rounding.
 */
object EnergyTargets {

    fun forProfile(raw: Profile): Macros {
        val p = raw.clampedForCalculations() // stored profiles predating validation (VT-AUD-035)
        val kcal = targetKcal(p)
        val proteinMultiplier = when (p.goal) {
            Goal.GAIN_MUSCLE -> 1.8
            Goal.LOSE_FAT -> 1.6
            Goal.MAINTAIN -> 1.7
        }
        val protein = (p.weightKg * proteinMultiplier).toInt()
            .coerceIn((p.weightKg * 1.4).toInt(), (p.weightKg * 2.2).toInt())
        val fat = max(0.9 * p.weightKg, 40.0).toInt()
        return fitToBudget(kcal, protein, fat)
    }

    /** Makes protein/fat fit [kcal] (fat first, down to 20% of kcal, then protein); carbs = rest. */
    fun fitToBudget(kcal: Int, proteinG: Int, fatG: Int): Macros {
        var fat = fatG
        var protein = proteinG
        if (protein * 4 + fat * 9 > kcal) {
            val fatFloor = (kcal * 0.20 / 9).toInt()
            fat = ((kcal - protein * 4) / 9).coerceIn(fatFloor, fat)
        }
        if (protein * 4 + fat * 9 > kcal) {
            protein = ((kcal - fat * 9) / 4).coerceAtLeast(0)
        }
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).coerceAtLeast(0)
        return Macros(proteinGrams = protein, fatGrams = fat, carbsGrams = carbs, kcal = kcal)
    }

    private fun targetKcal(p: Profile): Int {
        val s = if (p.sex == Sex.MALE) 5.0 else -161.0
        val bmr = 10.0 * p.weightKg + 6.25 * p.heightCm - 5.0 * p.age + s
        val lifestyleBase = when (p.lifestyleActivity) {
            LifestyleActivity.LIGHT -> 1.30
            LifestyleActivity.ACTIVE -> 1.45
            LifestyleActivity.VERY_ACTIVE -> 1.60
            LifestyleActivity.SEDENTARY -> 1.20
        }
        val trainingDays = p.weeklySchedule.count { it.value }
        val trainingBonus = when {
            trainingDays == 0 -> 0.00
            trainingDays <= 2 -> 0.10
            trainingDays <= 4 -> 0.15
            else -> 0.20
        }
        val activityFactor = (lifestyleBase + trainingBonus).coerceAtMost(1.90)
        val calorieFactor = when (p.goal) {
            Goal.LOSE_FAT -> 0.85
            Goal.GAIN_MUSCLE -> 1.10
            Goal.MAINTAIN -> 1.00
        }
        return (bmr * activityFactor * calorieFactor).coerceAtLeast(bmr).toInt()
    }
}

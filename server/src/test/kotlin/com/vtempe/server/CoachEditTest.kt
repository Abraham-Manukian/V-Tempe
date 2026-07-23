package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.CoachEditApplicator
import com.vtempe.server.features.ai.data.service.NormalizationMode
import com.vtempe.server.features.ai.data.service.normalizeTrainingPlan
import com.vtempe.server.shared.dto.chat.CoachEditOp
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CoachEditTest {

    private val catalog = BuiltInExerciseCatalog()
    private val resolver = DefaultTrainingPlanResolver(catalog)
    private val applicator = CoachEditApplicator(catalog, resolver)

    private fun gymProfile() = AiProfile(
        age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0,
        goal = "MAINTAIN", experienceLevel = 5,
        equipment = listOf("barbell", "bench", "dumbbells"),
        weeklySchedule = mapOf("Mon" to true, "Wed" to true, "Fri" to true),
        trainingMode = "gym",
    )

    private fun planWith(vararg exerciseIds: String) = AiTrainingResponse(
        weekIndex = 0,
        workouts = listOf(
            AiWorkout(
                id = "w_0_0",
                label = "Full Body",
                date = "2026-07-24",
                sets = exerciseIds.map { AiSet(exerciseId = it, reps = 8, weightKg = 50.0, rpe = 7.5) },
            )
        ),
    )

    // ── EDIT-mode normalization must NOT rebuild the skeleton ────────────────────

    @Test
    fun `EDIT mode preserves the AI's exercises instead of reskeletoning`() {
        // A plan where the user swapped in lat_pulldown — GENERATE would overwrite it from the
        // deterministic skeleton; EDIT must keep it.
        val edited = planWith("squat", "bench", "lat_pulldown")
        val out = normalizeTrainingPlan(
            edited, gymProfile(), resolver, mode = NormalizationMode.EDIT
        )
        val ids = out.workouts.single().sets.map { it.exerciseId }
        assertTrue(ids.contains("lat_pulldown"), "expected lat_pulldown kept, got $ids")
        assertTrue(ids.contains("squat"), "expected squat kept, got $ids")
    }

    @Test
    fun `GENERATE mode still rebuilds from the skeleton`() {
        val edited = planWith("lat_pulldown")
        val out = normalizeTrainingPlan(
            edited, gymProfile(), resolver, mode = NormalizationMode.GENERATE
        )
        // Skeleton-driven output has a full session, not just the single set the AI returned.
        assertTrue(out.workouts.single().sets.size > 1, "skeleton should expand the session")
    }

    // ── Applicator: training ─────────────────────────────────────────────────────

    @Test
    fun `swap_exercise replaces the target and reports changed`() {
        val result = applicator.apply(
            ops = listOf(CoachEditOp(op = "swap_exercise", exerciseId = "bench", newExerciseId = "ohp")),
            currentTraining = planWith("squat", "bench"),
            currentNutrition = null,
            profile = gymProfile(),
        )
        assertTrue(result.trainingChanged)
        val ids = result.trainingPlan!!.workouts.single().sets.map { it.exerciseId }
        assertTrue(ids.contains("ohp"))
        assertFalse(ids.contains("bench"))
    }

    @Test
    fun `swap to an unknown exercise is rejected, nothing changes`() {
        val result = applicator.apply(
            ops = listOf(CoachEditOp(op = "swap_exercise", exerciseId = "squat", newExerciseId = "flying_unicorn_press")),
            currentTraining = planWith("squat", "bench"),
            currentNutrition = null,
            profile = gymProfile(),
        )
        assertFalse(result.trainingChanged)
        assertNull(result.trainingPlan)
        assertTrue(result.rejections.isNotEmpty())
    }

    @Test
    fun `swap accepts a resolver pattern token as the replacement`() {
        // The AI may emit a slot token instead of a concrete id — it must still resolve to a real
        // catalog exercise the user can do, in the user's equipment context.
        val result = applicator.apply(
            ops = listOf(CoachEditOp(op = "swap_exercise", exerciseId = "bench", newExerciseId = "pattern:vertical_pull")),
            currentTraining = planWith("squat", "bench"),
            currentNutrition = null,
            profile = gymProfile(),
        )
        assertTrue(result.trainingChanged)
        val ids = result.trainingPlan!!.workouts.single().sets.map { it.exerciseId }
        assertFalse(ids.contains("bench"))
        assertFalse(ids.any { it.startsWith("pattern:") }, "token must resolve to a concrete id, got $ids")
    }

    @Test
    fun `set_weight updates only the matching exercise`() {
        val result = applicator.apply(
            ops = listOf(CoachEditOp(op = "set_weight", exerciseId = "squat", weightKg = 100.0)),
            currentTraining = planWith("squat", "bench"),
            currentNutrition = null,
            profile = gymProfile(),
        )
        assertTrue(result.trainingChanged)
        val sets = result.trainingPlan!!.workouts.single().sets
        assertEquals(100.0, sets.first { it.exerciseId == "squat" }.weightKg)
        assertEquals(50.0, sets.first { it.exerciseId == "bench" }.weightKg)
    }

    @Test
    fun `remove_exercise never empties a workout to zero sets`() {
        val result = applicator.apply(
            ops = listOf(CoachEditOp(op = "remove_exercise", exerciseId = "squat")),
            currentTraining = planWith("squat"),
            currentNutrition = null,
            profile = gymProfile(),
        )
        assertFalse(result.trainingChanged, "removing the only exercise should be refused")
    }

    // ── Applicator: nutrition ────────────────────────────────────────────────────

    private fun nutritionPlan() = AiNutritionResponse(
        weekIndex = 0,
        mealsByDay = mapOf(
            "Mon" to listOf(
                AiMeal(
                    name = "Овсянка",
                    ingredients = listOf("150 г овсянки", "250 мл молока"),
                    kcal = 360,
                    macros = Macros(10, 6, 64, 358),
                ),
                AiMeal(
                    name = "Курица с рисом",
                    ingredients = listOf("180 г курицы", "150 г риса"),
                    kcal = 520,
                    macros = Macros(45, 12, 55, 520),
                ),
            )
        ),
    )

    @Test
    fun `set_ingredient replaces the targeted ingredient`() {
        val result = applicator.apply(
            ops = listOf(
                CoachEditOp(op = "set_ingredient", day = "Mon", mealIndex = 0, ingredientIndex = 1, ingredient = "200 мл кефира")
            ),
            currentTraining = null,
            currentNutrition = nutritionPlan(),
            profile = null,
        )
        assertTrue(result.nutritionChanged)
        val meal = result.nutritionPlan!!.mealsByDay["Mon"]!!.first()
        assertEquals("200 мл кефира", meal.ingredients[1])
    }

    @Test
    fun `swap_meal by fuzzy name replaces the whole meal`() {
        val result = applicator.apply(
            ops = listOf(
                CoachEditOp(
                    op = "swap_meal", day = "Mon", mealName = "курица",
                    name = "Лосось с гречкой",
                    ingredients = listOf("200 г лосося", "150 г гречки"),
                    proteinGrams = 40, fatGrams = 20, carbsGrams = 45, kcal = 560,
                )
            ),
            currentTraining = null,
            currentNutrition = nutritionPlan(),
            profile = null,
        )
        assertTrue(result.nutritionChanged)
        val names = result.nutritionPlan!!.mealsByDay["Mon"]!!.map { it.name }
        assertTrue(names.contains("Лосось с гречкой"))
        assertFalse(names.contains("Курица с рисом"))
    }

    @Test
    fun `editing a missing day is rejected`() {
        val result = applicator.apply(
            ops = listOf(CoachEditOp(op = "rename_meal", day = "Xyz", mealIndex = 0, name = "X")),
            currentTraining = null,
            currentNutrition = nutritionPlan(),
            profile = null,
        )
        assertFalse(result.nutritionChanged)
        assertTrue(result.rejections.isNotEmpty())
    }

    @Test
    fun `empty ops produce no change`() {
        val result = applicator.apply(emptyList(), planWith("squat"), nutritionPlan(), gymProfile())
        assertFalse(result.trainingChanged)
        assertFalse(result.nutritionChanged)
    }
}

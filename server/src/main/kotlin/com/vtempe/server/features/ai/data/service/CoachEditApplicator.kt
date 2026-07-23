package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.chat.CoachEditOp
import com.vtempe.server.shared.dto.chat.CoachEditOpType
import com.vtempe.server.shared.dto.chat.EditDomain
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout

/**
 * Applies the coach's surgical [CoachEditOp]s to the user's CURRENT plans, server-side.
 *
 * This is the deterministic half of the hybrid edit contract: the AI decides *what* to change and
 * emits ops; this class actually mutates the plan and guarantees every exercise stays inside the
 * catalog. Nothing here trusts the AI to have produced a valid or complete plan — an op targeting
 * something that doesn't exist, or naming an exercise outside the catalog, is skipped and reported
 * back so the reply can be honest instead of claiming a change that didn't happen.
 */
class CoachEditApplicator(
    private val catalog: ExerciseCatalog,
    private val resolver: TrainingPlanResolver,
) {
    data class Result(
        val trainingPlan: AiTrainingResponse?,
        val nutritionPlan: AiNutritionResponse?,
        val trainingChanged: Boolean,
        val nutritionChanged: Boolean,
        /** Human-readable notes about ops that could not be applied (unknown exercise, missing
         *  target). Surfaced in the coach reply so the user isn't told a change happened when it
         *  didn't. */
        val rejections: List<String>,
    )

    fun apply(
        ops: List<CoachEditOp>,
        currentTraining: AiTrainingResponse?,
        currentNutrition: AiNutritionResponse?,
        profile: AiProfile?,
    ): Result {
        if (ops.isEmpty()) {
            return Result(null, null, false, false, emptyList())
        }
        val rejections = mutableListOf<String>()

        var training = currentTraining
        var trainingTouched = false
        var nutrition = currentNutrition
        var nutritionTouched = false

        ops.forEach { op ->
            val type = CoachEditOpType.fromWire(op.op)
            if (type == null) {
                rejections += "unknown op '${op.op}'"
                return@forEach
            }
            when (type.domain) {
                EditDomain.TRAINING -> {
                    if (training == null) {
                        rejections += "no current training plan to edit for '${op.op}'"
                        return@forEach
                    }
                    val (updated, changed) = applyTrainingOp(type, op, training!!, profile, rejections)
                    training = updated
                    if (changed) trainingTouched = true
                }
                EditDomain.NUTRITION -> {
                    if (nutrition == null) {
                        rejections += "no current nutrition plan to edit for '${op.op}'"
                        return@forEach
                    }
                    val (updated, changed) = applyNutritionOp(type, op, nutrition!!, rejections)
                    nutrition = updated
                    if (changed) nutritionTouched = true
                }
            }
        }

        return Result(
            trainingPlan = if (trainingTouched) training else null,
            nutritionPlan = if (nutritionTouched) nutrition else null,
            trainingChanged = trainingTouched,
            nutritionChanged = nutritionTouched,
            rejections = rejections,
        )
    }

    // ── Training ─────────────────────────────────────────────────────────

    private fun applyTrainingOp(
        type: CoachEditOpType,
        op: CoachEditOp,
        plan: AiTrainingResponse,
        profile: AiProfile?,
        rejections: MutableList<String>,
    ): Pair<AiTrainingResponse, Boolean> {
        var changed = false

        fun editWorkoutSets(transform: (AiWorkout) -> Pair<AiWorkout, Boolean>): AiTrainingResponse {
            val workouts = plan.workouts.map { workout ->
                if (op.workoutId != null && workout.id != op.workoutId) return@map workout
                val (updated, didChange) = transform(workout)
                if (didChange) changed = true
                updated
            }
            return plan.copy(workouts = workouts)
        }

        val result: AiTrainingResponse = when (type) {
            CoachEditOpType.SWAP_EXERCISE -> {
                val target = normalizeExerciseToken(op.exerciseId.orEmpty())
                val replacement = resolveExerciseOrNull(op.newExerciseId)
                if (target.isBlank() || replacement == null) {
                    rejections += "cannot swap to '${op.newExerciseId ?: "?"}' — not a known exercise"
                    return plan to false
                }
                editWorkoutSets { workout ->
                    var did = false
                    val sets = workout.sets.map { set ->
                        if (normalizeExerciseToken(set.exerciseId) != target) return@map set
                        did = true
                        set.copy(
                            exerciseId = replacement,
                            weightKg = if (isBodyweightOnly(replacement)) null else set.weightKg,
                        )
                    }
                    workout.copy(sets = dedupeSets(sets)) to did
                }
            }

            CoachEditOpType.SET_WEIGHT -> mutateMatchingSets(op, ::editWorkoutSets) { set, id ->
                if (isBodyweightOnly(id)) set.copy(weightKg = null)
                else set.copy(weightKg = op.weightKg?.takeIf { it >= 0.0 })
            }

            CoachEditOpType.SET_REPS -> mutateMatchingSets(op, ::editWorkoutSets) { set, id ->
                val reps = op.reps?.let { clampRepsForUnit(id, it.coerceAtLeast(1)) } ?: set.reps
                set.copy(reps = reps)
            }

            CoachEditOpType.SET_RPE -> mutateMatchingSets(op, ::editWorkoutSets) { set, _ ->
                set.copy(rpe = op.rpe?.takeIf { it > 0.0 } ?: set.rpe)
            }

            CoachEditOpType.SET_SETS -> mutateMatchingSets(op, ::editWorkoutSets) { set, _ ->
                set.copy(sets = op.sets?.coerceIn(1, 10) ?: set.sets)
            }

            CoachEditOpType.ADD_EXERCISE -> {
                val newId = resolveExerciseOrNull(op.newExerciseId ?: op.exerciseId)
                if (newId == null) {
                    rejections += "cannot add '${op.newExerciseId ?: op.exerciseId ?: "?"}' — not a known exercise"
                    return plan to false
                }
                if (op.workoutId == null) {
                    rejections += "add_exercise needs a workoutId"
                    return plan to false
                }
                editWorkoutSets { workout ->
                    if (workout.sets.any { normalizeExerciseToken(it.exerciseId) == newId }) {
                        workout to false // already present — nothing to add
                    } else {
                        val newSet = AiSet(
                            exerciseId = newId,
                            reps = op.reps?.let { clampRepsForUnit(newId, it.coerceAtLeast(1)) } ?: 8,
                            weightKg = if (isBodyweightOnly(newId)) null else op.weightKg?.takeIf { it >= 0.0 },
                            rpe = op.rpe?.takeIf { it > 0.0 } ?: 7.5,
                            sets = op.sets?.coerceIn(1, 10) ?: 3,
                        )
                        workout.copy(sets = (workout.sets + newSet).take(MaxSetsPerWorkout)) to true
                    }
                }
            }

            CoachEditOpType.REMOVE_EXERCISE -> {
                val target = normalizeExerciseToken(op.exerciseId.orEmpty())
                if (target.isBlank()) {
                    rejections += "remove_exercise needs an exerciseId"
                    return plan to false
                }
                editWorkoutSets { workout ->
                    val remaining = workout.sets.filterNot { normalizeExerciseToken(it.exerciseId) == target }
                    // Never empty a workout to zero sets — validateTrainingPlan rejects that.
                    if (remaining.isEmpty() || remaining.size == workout.sets.size) {
                        workout to false
                    } else {
                        workout.copy(sets = remaining) to true
                    }
                }
            }

            else -> plan // not a training op
        }
        return result to changed
    }

    /** Shared body for the SET_* ops: apply [transform] to every set whose exercise matches
     *  op.exerciseId (or every set, when op.exerciseId is absent). */
    private inline fun mutateMatchingSets(
        op: CoachEditOp,
        editWorkoutSets: ((AiWorkout) -> Pair<AiWorkout, Boolean>) -> AiTrainingResponse,
        crossinline transform: (AiSet, String) -> AiSet,
    ): AiTrainingResponse {
        val target = op.exerciseId?.let { normalizeExerciseToken(it) }?.takeIf { it.isNotBlank() }
        return editWorkoutSets { workout ->
            var did = false
            val sets = workout.sets.map { set ->
                val id = normalizeExerciseToken(set.exerciseId)
                if (target != null && id != target) return@map set
                val updated = transform(set, id)
                if (updated != set) did = true
                updated
            }
            workout.copy(sets = sets) to did
        }
    }

    private fun dedupeSets(sets: List<AiSet>): List<AiSet> =
        sets.distinctBy { normalizeExerciseToken(it.exerciseId) }.take(MaxSetsPerWorkout)

    private fun resolveExerciseOrNull(raw: String?): String? {
        val token = normalizeExerciseToken(raw.orEmpty())
        if (token.isBlank()) return null
        return catalog.findByIdOrAlias(token)?.id
            ?: resolver.resolveExerciseId(rawToken = raw!!, trainingModeRaw = null, equipment = emptyList())
    }

    // ── Nutrition ────────────────────────────────────────────────────────

    private fun applyNutritionOp(
        type: CoachEditOpType,
        op: CoachEditOp,
        plan: AiNutritionResponse,
        rejections: MutableList<String>,
    ): Pair<AiNutritionResponse, Boolean> {
        val dayKey = op.day?.let { canonicalDayKey(it) }
        if (dayKey == null || plan.mealsByDay[dayKey] == null) {
            rejections += "cannot find day '${op.day ?: "?"}' to edit"
            return plan to false
        }
        val meals = plan.mealsByDay[dayKey]!!.toMutableList()

        // ADD_MEAL / SWAP_MEAL can create/replace a whole meal; the rest target an existing meal.
        if (type == CoachEditOpType.ADD_MEAL) {
            val meal = buildMealFromOp(op) ?: run {
                rejections += "add_meal needs a name and ingredients"
                return plan to false
            }
            meals.add(meal)
            return plan.copy(mealsByDay = plan.mealsByDay + (dayKey to meals)) to true
        }

        val mealIndex = resolveMealIndex(op, meals)
        if (mealIndex == null) {
            rejections += "cannot find meal '${op.mealName ?: op.mealIndex ?: "?"}' on $dayKey"
            return plan to false
        }
        val meal = meals[mealIndex]
        var changed = false

        val updatedMeal: AiMeal? = when (type) {
            CoachEditOpType.SET_INGREDIENT -> {
                val idx = op.ingredientIndex ?: fuzzyIngredientIndex(meal.ingredients, op.ingredient)
                val newText = op.ingredient?.let { sanitizeText(it) }?.takeIf { it.isNotEmpty() }
                if (idx == null || idx !in meal.ingredients.indices || newText == null) {
                    rejections += "cannot set ingredient on '${meal.name}'"
                    meal
                } else {
                    changed = true
                    meal.copy(ingredients = meal.ingredients.toMutableList().apply { this[idx] = newText })
                }
            }
            CoachEditOpType.ADD_INGREDIENT -> {
                val newText = op.ingredient?.let { sanitizeText(it) }?.takeIf { it.isNotEmpty() }
                if (newText == null) { rejections += "add_ingredient needs ingredient text"; meal }
                else { changed = true; meal.copy(ingredients = meal.ingredients + newText) }
            }
            CoachEditOpType.REMOVE_INGREDIENT -> {
                val idx = op.ingredientIndex ?: fuzzyIngredientIndex(meal.ingredients, op.ingredient)
                if (idx == null || idx !in meal.ingredients.indices || meal.ingredients.size <= 1) {
                    rejections += "cannot remove ingredient from '${meal.name}'"
                    meal
                } else {
                    changed = true
                    meal.copy(ingredients = meal.ingredients.filterIndexed { i, _ -> i != idx })
                }
            }
            CoachEditOpType.SET_MEAL_MACROS -> {
                val m = meal.macros
                val newMacros = Macros(
                    proteinGrams = op.proteinGrams?.coerceAtLeast(0) ?: m.proteinGrams,
                    fatGrams = op.fatGrams?.coerceAtLeast(0) ?: m.fatGrams,
                    carbsGrams = op.carbsGrams?.coerceAtLeast(0) ?: m.carbsGrams,
                    kcal = op.kcal?.coerceAtLeast(0) ?: m.kcal,
                )
                val normalized = normalizeMacros(newMacros)
                changed = normalized != meal.macros
                meal.copy(macros = normalized, kcal = normalized.kcal)
            }
            CoachEditOpType.RENAME_MEAL -> {
                val newName = op.name?.let { sanitizeText(it) }?.takeIf { it.isNotEmpty() }
                if (newName == null) { rejections += "rename_meal needs a name"; meal }
                else { changed = newName != meal.name; meal.copy(name = newName) }
            }
            CoachEditOpType.SWAP_MEAL -> {
                buildMealFromOp(op)?.also { changed = true }
                    ?: run { rejections += "swap_meal needs a name and ingredients"; meal }
            }
            CoachEditOpType.REMOVE_MEAL -> {
                if (meals.size <= 1) { rejections += "cannot remove the only meal on $dayKey"; meal }
                else { changed = true; null } // signal removal
            }
            else -> meal
        }

        if (!changed) return plan to false
        if (updatedMeal == null) {
            meals.removeAt(mealIndex)
        } else {
            meals[mealIndex] = updatedMeal
        }
        return plan.copy(mealsByDay = plan.mealsByDay + (dayKey to meals)) to true
    }

    private fun buildMealFromOp(op: CoachEditOp): AiMeal? {
        val name = op.name?.let { sanitizeText(it) }?.takeIf { it.isNotEmpty() } ?: return null
        val ingredients = op.ingredients?.map { sanitizeText(it) }?.filter { it.isNotEmpty() }
            ?: return null
        if (ingredients.isEmpty()) return null
        val macros = normalizeMacros(
            Macros(
                proteinGrams = op.proteinGrams?.coerceAtLeast(0) ?: 0,
                fatGrams = op.fatGrams?.coerceAtLeast(0) ?: 0,
                carbsGrams = op.carbsGrams?.coerceAtLeast(0) ?: 0,
                kcal = op.kcal?.coerceAtLeast(0) ?: 0,
            )
        )
        return AiMeal(
            name = name,
            ingredients = ingredients,
            kcal = macros.kcal,
            macros = macros,
            recipe = op.recipe?.let { sanitizeText(it) }.orEmpty(),
        )
    }

    private fun resolveMealIndex(op: CoachEditOp, meals: List<AiMeal>): Int? {
        op.mealIndex?.let { if (it in meals.indices) return it }
        val name = op.mealName?.let { sanitizeText(it) }?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return null
        val exact = meals.indexOfFirst { it.name.lowercase() == name }
        if (exact >= 0) return exact
        val fuzzy = meals.indexOfFirst { it.name.lowercase().contains(name) || name.contains(it.name.lowercase()) }
        return fuzzy.takeIf { it >= 0 }
    }

    private fun fuzzyIngredientIndex(ingredients: List<String>, target: String?): Int? {
        val t = target?.let { sanitizeText(it) }?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        val exact = ingredients.indexOfFirst { it.lowercase() == t }
        if (exact >= 0) return exact
        val fuzzy = ingredients.indexOfFirst { ing ->
            val words = t.split(" ").filter { it.length > 2 }
            words.any { ing.lowercase().contains(it) }
        }
        return fuzzy.takeIf { it >= 0 }
    }
}

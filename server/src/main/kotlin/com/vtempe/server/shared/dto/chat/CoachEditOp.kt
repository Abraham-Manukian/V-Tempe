package com.vtempe.server.shared.dto.chat

import kotlinx.serialization.Serializable

/**
 * A single surgical edit the coach wants to apply to the user's CURRENT plan, applied
 * server-side by CoachEditApplicator against the plan the client sent in the request. This is
 * the cheap, precise path for small changes ("set bench to 60kg", "swap salmon for chicken on
 * Tuesday", "remove olive oil") — the AI returns only the delta instead of rewriting the whole
 * plan, which both saves tokens and removes the chance of the AI corrupting an untouched part of
 * the plan while copying it.
 *
 * editOps never reach the client: the applicator turns them into a fully-formed
 * trainingPlan/nutritionPlan on the response, so the client contract (save whatever plan comes
 * back) is unchanged. For large changes ("regenerate my whole leg day") the AI still returns a
 * full plan object instead.
 *
 * All targeting fields are optional; each [op] uses only the ones it needs. Unknown ops and ops
 * whose target can't be located are skipped and reported back in the reply, never applied blindly.
 */
@Serializable
data class CoachEditOp(
    /** One of [CoachEditOpType] wire names. */
    val op: String,

    // ── Training targeting ───────────────────────────────────────────────
    /** Which workout to edit. Optional — when absent the op applies to every workout that
     *  contains [exerciseId] (e.g. "make all my squats 3 sets"). */
    val workoutId: String? = null,
    /** The exercise being edited/removed/swapped-from (catalog id or alias). */
    val exerciseId: String? = null,
    /** For swap_exercise / add_exercise — the exercise being swapped/added in (catalog id or alias). */
    val newExerciseId: String? = null,
    val weightKg: Double? = null,
    val reps: Int? = null,
    val rpe: Double? = null,
    val sets: Int? = null,

    // ── Nutrition targeting ──────────────────────────────────────────────
    /** Day key: Mon..Sun (English or localized — normalized server-side). */
    val day: String? = null,
    /** Index of the meal within the day, 0-based. Optional when [mealName] is given. */
    val mealIndex: Int? = null,
    /** Alternative meal targeting by (fuzzy) name when the AI doesn't know the index. */
    val mealName: String? = null,
    /** Index of the ingredient within the meal, 0-based. Optional when [ingredient] identifies it. */
    val ingredientIndex: Int? = null,
    /** New/target ingredient text ("150 г риса"). For set_ingredient this is the replacement;
     *  for add_ingredient the addition; for remove_ingredient a fuzzy match to find the target. */
    val ingredient: String? = null,
    /** New meal name for rename_meal / the name of an added or swapped-in meal. */
    val name: String? = null,
    val kcal: Int? = null,
    val proteinGrams: Int? = null,
    val fatGrams: Int? = null,
    val carbsGrams: Int? = null,
    /** Full ingredient list for swap_meal / add_meal. */
    val ingredients: List<String>? = null,
    val recipe: String? = null,
)

/** Wire names must match exactly what the prompt tells the AI to emit. */
enum class CoachEditOpType(val wire: String, val domain: EditDomain) {
    // Training
    SWAP_EXERCISE("swap_exercise", EditDomain.TRAINING),
    SET_WEIGHT("set_weight", EditDomain.TRAINING),
    SET_REPS("set_reps", EditDomain.TRAINING),
    SET_RPE("set_rpe", EditDomain.TRAINING),
    SET_SETS("set_sets", EditDomain.TRAINING),
    ADD_EXERCISE("add_exercise", EditDomain.TRAINING),
    REMOVE_EXERCISE("remove_exercise", EditDomain.TRAINING),

    // Nutrition
    SET_INGREDIENT("set_ingredient", EditDomain.NUTRITION),
    ADD_INGREDIENT("add_ingredient", EditDomain.NUTRITION),
    REMOVE_INGREDIENT("remove_ingredient", EditDomain.NUTRITION),
    SET_MEAL_MACROS("set_meal_macros", EditDomain.NUTRITION),
    RENAME_MEAL("rename_meal", EditDomain.NUTRITION),
    SWAP_MEAL("swap_meal", EditDomain.NUTRITION),
    ADD_MEAL("add_meal", EditDomain.NUTRITION),
    REMOVE_MEAL("remove_meal", EditDomain.NUTRITION);

    companion object {
        fun fromWire(raw: String?): CoachEditOpType? =
            raw?.trim()?.lowercase()?.let { w -> entries.firstOrNull { it.wire == w } }
    }
}

enum class EditDomain { TRAINING, NUTRITION }

package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.profile.AiProfile

/**
 * Every food-safety finding contains this marker. [AiQualityErrorPolicy] treats any error with
 * it as critical in every quality mode, whatever else the message happens to say.
 */
internal const val AllergenViolationMarker = "allergen restriction violation"

/** Thrown when no meal at all can be built without the user's forbidden foods. Callers must
 *  surface it as a refusal instead of shipping a plan that ignores the restrictions. */
internal class NoSafeNutritionPlanException(message: String) : IllegalStateException(message)

/**
 * The mandatory food-safety gate (VT-AUD-026, VT-AUD-027): every allergen/diet violation in a
 * plan — meal names, ingredients, recipes, LLM-declared allergen tags and the shopping list.
 *
 * Runs to completion independently of any quality findings or report-size limits, so an
 * allergen can never be hidden behind earlier warnings. An empty result means "safe to serve".
 */
internal fun nutritionSafetyViolations(plan: AiNutritionResponse, restrictions: NutritionRestrictions): List<String> {
    if (restrictions.isEmpty) return emptyList()
    val violations = mutableListOf<String>()
    plan.mealsByDay.forEach { (day, meals) ->
        meals.forEachIndexed { mealIndex, meal ->
            mealSafetyFindings(meal, restrictions).forEach { (field, reason) ->
                violations += "$AllergenViolationMarker in $day meal[$mealIndex]$field: $reason"
            }
        }
    }
    plan.shoppingList.forEachIndexed { index, item ->
        restrictionViolationReason(item, restrictions)?.let { reason ->
            violations += "$AllergenViolationMarker in shoppingList[$index]: $reason"
        }
    }
    return violations.distinct()
}

internal fun nutritionSafetyViolations(plan: AiNutritionResponse, profile: AiProfile): List<String> =
    nutritionSafetyViolations(plan, buildNutritionRestrictions(profile))

/** Location-free reasons why [meal] breaks [restrictions]; empty when it complies. */
internal fun mealSafetyViolations(meal: AiMeal, restrictions: NutritionRestrictions): Set<String> =
    if (restrictions.isEmpty) emptySet() else mealSafetyFindings(meal, restrictions).map { it.second }.toSet()

/** (field suffix, reason) pairs. Allergen tags declared by the model and the text of every
 *  field are both checked — neither one alone is trusted to prove the meal is safe. */
private fun mealSafetyFindings(meal: AiMeal, restrictions: NutritionRestrictions): List<Pair<String, String>> {
    val findings = mutableListOf<Pair<String, String>>()
    meal.allergenTags
        .mapNotNull { tagName -> FoodRestrictionTag.entries.firstOrNull { it.name.equals(tagName.trim(), ignoreCase = true) } }
        .filter { it in restrictions.tags }
        .forEach { tag -> findings += ".allergenTags" to "LLM tagged '${tag.name}' which is forbidden for this user" }
    restrictionViolationReason(meal.name, restrictions)?.let { findings += ".name" to it }
    meal.ingredients.forEachIndexed { index, ingredient ->
        restrictionViolationReason(ingredient, restrictions)?.let { findings += ".ingredients[$index]" to it }
    }
    recipeClauses(meal.recipe).forEach { clause ->
        restrictionViolationReason(clause, restrictions)?.let { findings += ".recipe" to it }
    }
    return findings
}

/**
 * A recipe is free text: one sentence can negate a food ("serve without nuts") while the next
 * adds it. Checking clause by clause keeps a negation from leaking past its own clause
 * ("cook the rice without salt and add peanut butter"). Splitting also at "and"/"и" can flag
 * a harmless "без молока и орехов" — accepted: a false alarm costs a retry, a miss costs a
 * reaction.
 */
private val recipeClauseBreak = Regex("""[.!?;:\n]+|\s+(?:and|then|и|затем|потом|после)\s+""")

private fun recipeClauses(recipe: String): List<String> =
    recipe.lowercase().split(recipeClauseBreak).map { it.trim() }.filter { it.isNotEmpty() }

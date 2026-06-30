package com.vtempe.server.features.ai.data.service.nutrition

import com.vtempe.server.features.ai.data.service.FoodRestrictionTag
import com.vtempe.server.features.ai.data.service.buildNutritionRestrictions
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import java.util.Locale

/**
 * Public, testable facade over the food-restriction logic in AiNutritionSafetyPolicy.kt.
 *
 * Restriction *parsing* (vegan/vegetarian/allergy detection, including Russian keywords and
 * negation handling) is delegated to [buildNutritionRestrictions] so there is a single source
 * of truth. This object exposes a stable, coarse-grained API used by tests and any caller that
 * wants a quick yes/no on whether a meal violates the user's restrictions.
 */
object FoodRestrictionValidator {

    data class FoodRestrictions(
        val noFish: Boolean = false,
        val noShellfish: Boolean = false,
        val noMeat: Boolean = false,
        val noPoultry: Boolean = false,
        val noDairy: Boolean = false,
        val noEggs: Boolean = false,
        val customTerms: Set<String> = emptySet()
    )

    /** Parses a profile into coarse-grained boolean restrictions (delegates to canonical logic). */
    fun parse(profile: AiProfile): FoodRestrictions {
        val r = buildNutritionRestrictions(profile)
        return FoodRestrictions(
            noFish = FoodRestrictionTag.Fish in r.tags,
            noShellfish = FoodRestrictionTag.Shellfish in r.tags,
            noMeat = FoodRestrictionTag.Meat in r.tags,
            noPoultry = FoodRestrictionTag.Poultry in r.tags,
            noDairy = FoodRestrictionTag.Dairy in r.tags || FoodRestrictionTag.Lactose in r.tags,
            noEggs = FoodRestrictionTag.Egg in r.tags,
            customTerms = r.customTerms
        )
    }

    /**
     * Returns coarse violation labels (e.g. "fish/seafood", "meat") found in a single meal's
     * name + ingredients, given parsed [restrictions]. Empty list = compliant.
     */
    fun containsForbiddenFood(
        mealName: String,
        ingredients: List<String>,
        restrictions: FoodRestrictions
    ): List<String> {
        val text = (listOf(mealName) + ingredients).joinToString(" ").lowercase(Locale.ROOT)
        val violations = mutableListOf<String>()
        fun check(condition: Boolean, keywords: List<String>, label: String) {
            if (condition && keywords.any { text.contains(it) }) violations.add(label)
        }
        check(restrictions.noFish, FISH_KEYWORDS, "fish/seafood")
        check(restrictions.noShellfish, SHELLFISH_KEYWORDS, "shellfish")
        check(restrictions.noMeat, MEAT_KEYWORDS, "meat")
        check(restrictions.noPoultry, POULTRY_KEYWORDS, "poultry")
        check(restrictions.noDairy, DAIRY_KEYWORDS, "dairy")
        check(restrictions.noEggs, EGG_KEYWORDS, "eggs")
        return violations
    }

    /** Validates an entire plan; returns one description per offending meal. Empty = ok. */
    fun validatePlan(plan: AiNutritionResponse, profile: AiProfile): List<String> {
        val restrictions = parse(profile)
        val violations = mutableListOf<String>()
        plan.mealsByDay.forEach { (day, meals) ->
            meals.forEach { meal: AiMeal ->
                val v = containsForbiddenFood(meal.name, meal.ingredients, restrictions)
                if (v.isNotEmpty()) violations.add("Day $day / ${meal.name}: forbidden ${v.joinToString()}")
            }
        }
        return violations
    }

    private val FISH_KEYWORDS = listOf(
        "рыб", "тунц", "тунец", "сардин", "лосос", "минтай", "хек", "треск", "форел", "сельд",
        "скумбр", "fish", "tuna", "salmon", "cod", "sardine", "seafood", "морепродукт"
    )
    private val SHELLFISH_KEYWORDS = listOf(
        "креветк", "кальмар", "осьминог", "мид", "устриц", "омар", "shrimp", "prawn",
        "squid", "octopus", "mussel", "oyster", "lobster"
    )
    private val MEAT_KEYWORDS = listOf(
        "говяд", "свинин", "баранин", "телят", "фарш", "стейк", "шашлык",
        "beef", "pork", "lamb", "veal", "steak", "meat"
    )
    private val POULTRY_KEYWORDS = listOf(
        "курин", "куриц", "индейк", "утк", "птиц", "chicken", "turkey", "duck", "poultry"
    )
    private val DAIRY_KEYWORDS = listOf(
        "молок", "сыр", "творог", "сметан", "кефир", "йогурт", "сливк", "казеин", "сыворот",
        "milk", "cheese", "yogurt", "cream", "whey", "casein"
    )
    private val EGG_KEYWORDS = listOf("яйц", "яичн", "омлет", "глазунь", "egg", "omelet")
}

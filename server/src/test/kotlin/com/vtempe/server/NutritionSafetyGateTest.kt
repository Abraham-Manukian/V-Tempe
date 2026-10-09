package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.AiQualityErrorPolicy
import com.vtempe.server.features.ai.data.service.AllergenViolationMarker
import com.vtempe.server.features.ai.data.service.CoachEditApplicator
import com.vtempe.server.features.ai.data.service.NoSafeNutritionPlanException
import com.vtempe.server.features.ai.data.service.buildNutritionRestrictions
import com.vtempe.server.features.ai.data.service.fallbackNutrition
import com.vtempe.server.features.ai.data.service.normalizeNutritionPlan
import com.vtempe.server.features.ai.data.service.nutritionSafetyViolations
import com.vtempe.server.features.ai.data.service.restrictionViolationReason
import com.vtempe.server.features.ai.data.service.templateMeals
import com.vtempe.server.features.ai.data.service.validateNutritionPlan
import com.vtempe.server.shared.dto.chat.CoachEditOp
import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.nutrition.Macros
import com.vtempe.server.shared.dto.profile.AiProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/**
 * Regression tests for the mandatory nutrition safety gate: VT-AUD-026 (recipe), 027 (warning
 * budget), 028 (fallback substitutions) and chat edit ops. Pure functions, no LLM.
 */
class NutritionSafetyGateTest {

    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val peanutAllergy = profile(allergies = listOf("peanut allergy"))

    // ── VT-AUD-027 ─────────────────────────────────────────────────────────────

    @Test
    fun `quality warnings filling the report budget cannot hide an allergen`() {
        // Audit scenario: 7 frequency + 7 soft calorie warnings used to fill all 12 slots first.
        val profile = profile(goal = "GAIN_MUSCLE", allergies = listOf("peanut allergy"))
        val normalized = normalizeNutritionPlan(plan(profile, ingredient = "peanut butter"), Locale.ENGLISH, profile)

        val errors = validateNutritionPlan(normalized, profile, Locale.ENGLISH)

        assertTrue(errors.size <= 12)
        assertTrue(errors.any { AllergenViolationMarker in it }, errors.toString())
        assertTrue(AiQualityErrorPolicy.criticalErrors(errors).any { AllergenViolationMarker in it })
    }

    @Test
    fun `allergen findings stay critical even when quoted text looks like a soft warning`() {
        val error = "$AllergenViolationMarker in Mon meal[0].name: Peanut keyword 'peanut' in 'peanut calorie sum meal frequency'"
        assertEquals(listOf(error), AiQualityErrorPolicy.criticalErrors(listOf(error)))
        assertTrue(AiQualityErrorPolicy.warningErrors(listOf(error)).isEmpty())
    }

    @Test
    fun `plan without forbidden food keeps only its quality warnings`() {
        val normalized = normalizeNutritionPlan(plan(peanutAllergy, ingredient = "rice"), Locale.ENGLISH, peanutAllergy)
        val errors = validateNutritionPlan(normalized, peanutAllergy, Locale.ENGLISH)
        assertTrue(errors.none { AllergenViolationMarker in it }, errors.toString())
    }

    // ── VT-AUD-026 ─────────────────────────────────────────────────────────────

    @Test
    fun `allergen only in the recipe is rejected, a safe recipe passes`() {
        val recipeOnly = normalizeNutritionPlan(
            plan(peanutAllergy, ingredient = "rice", recipe = "Serve with peanut butter"),
            Locale.ENGLISH, peanutAllergy
        )
        val errors = validateNutritionPlan(recipeOnly, peanutAllergy, Locale.ENGLISH)
        assertTrue(AiQualityErrorPolicy.criticalErrors(errors).any { "recipe" in it && AllergenViolationMarker in it }, errors.toString())

        for (safeRecipe in listOf("Serve with vegetables", "Serve without peanuts", "Подавайте без арахиса.")) {
            val safe = normalizeNutritionPlan(plan(peanutAllergy, recipe = safeRecipe), Locale.ENGLISH, peanutAllergy)
            assertTrue(nutritionSafetyViolations(safe, peanutAllergy).isEmpty(), safeRecipe)
        }
    }

    @Test
    fun `a negation does not leak into the next recipe clause`() {
        for (recipe in listOf(
            "Cook the rice without salt and add peanut butter",
            "Варите рис без соли. Затем добавьте арахисовую пасту",
            "Без соли; посыпьте арахисом",
        )) {
            assertTrue(nutritionSafetyViolations(plan(peanutAllergy, recipe = recipe), peanutAllergy).isNotEmpty(), recipe)
        }
    }

    @Test
    fun `russian recipe with a forbidden food is rejected`() {
        val ru = profile(allergies = listOf("аллергия на арахис"))
        val violations = nutritionSafetyViolations(plan(ru, ingredient = "рис", recipe = "Подавайте с арахисовой пастой"), ru)
        assertTrue(violations.any { "recipe" in it }, violations.toString())
    }

    @Test
    fun `model allergen tags and text are both checked`() {
        // Tagged only as Gluten, but the ingredient is peanut butter.
        val mislabelled = plan(peanutAllergy, ingredient = "peanut butter", allergenTags = listOf("Gluten"))
        assertTrue(nutritionSafetyViolations(mislabelled, peanutAllergy).any { "ingredients" in it })
        // Text has no keyword, but the model itself declared peanuts.
        val declared = plan(peanutAllergy, ingredient = "satay sauce", allergenTags = listOf("Peanut"))
        assertTrue(nutritionSafetyViolations(declared, peanutAllergy).any { "allergenTags" in it })
        // The normalizer keeps the declaration for the gate.
        val normalized = normalizeNutritionPlan(declared, Locale.ENGLISH, peanutAllergy)
        assertTrue(nutritionSafetyViolations(normalized, peanutAllergy).isNotEmpty())
    }

    @Test
    fun `forbidden food in the shopping list is rejected`() {
        val violations = nutritionSafetyViolations(plan(peanutAllergy, shoppingList = listOf("rice", "peanut butter 200 g")), peanutAllergy)
        assertTrue(violations.any { "shoppingList[1]" in it }, violations.toString())
    }

    @Test
    fun `labels and plant alternatives only cover the word they belong to`() {
        val peanut = buildNutritionRestrictions(peanutAllergy)
        assertNotNull(restrictionViolationReason("gluten-free bread with peanut butter", peanut))
        assertNotNull(restrictionViolationReason("peanut-free granola with peanut butter", peanut))
        assertNull(restrictionViolationReason("peanut-free granola", peanut))
        assertNull(restrictionViolationReason("без арахиса", peanut))

        val dairy = buildNutritionRestrictions(profile(allergies = listOf("milk allergy")))
        assertNull(restrictionViolationReason("oat milk", dairy))
        assertNull(restrictionViolationReason("кокосовое молоко", dairy))
        assertNull(restrictionViolationReason("non-dairy creamer", dairy))
        assertNull(restrictionViolationReason("dairy-free yogurt", dairy))
        assertNotNull(restrictionViolationReason("non-fat milk", dairy))
        assertNotNull(restrictionViolationReason("рисовая лапша с сыром", dairy))
        assertNotNull(restrictionViolationReason("овсянка, молоко", dairy))
        assertNotNull(restrictionViolationReason("vegan bowl, greek yogurt", dairy))
    }

    // ── VT-AUD-028 ─────────────────────────────────────────────────────────────

    @Test
    fun `fallback never swaps a forbidden food for another forbidden one`() {
        val profile = profile(allergies = listOf("milk allergy", "rice allergy"))
        val fallback = fallbackNutrition(AiNutritionRequest(profile, weekIndex = 0, locale = "en"))

        val allText = fallback.mealsByDay.values.flatten().flatMap { it.ingredients + it.name } + fallback.shoppingList
        assertTrue(allText.none { "rice" in it.lowercase() || "milk" in it.lowercase() }, allText.toString())
        assertTrue(nutritionSafetyViolations(fallback, profile).isEmpty())
        assertTrue(fallback.mealsByDay.values.all { it.isNotEmpty() })
    }

    @Test
    fun `fallback with every replacement forbidden drops ingredients instead of reusing them`() {
        val profile = profile(
            allergies = listOf("milk", "rice", "quinoa", "vegetables", "fruit", "chickpeas", "pumpkin seeds"),
            dietaryPreferences = listOf("vegan"),
        )
        val fallback = fallbackNutrition(AiNutritionRequest(profile, weekIndex = 0, locale = "en"))
        assertTrue(nutritionSafetyViolations(fallback, profile).isEmpty())
        assertTrue(fallback.mealsByDay.values.flatten().isNotEmpty())
    }

    @Test
    fun `fallback refuses when no template meal can be made safe`() {
        val everything = listOf(
            "oats", "milk", "blueberries", "honey", "chicken", "rice", "broccoli", "carrots", "yogurt",
            "walnuts", "salmon", "quinoa", "spinach", "lemon", "vegetables", "fruit", "chickpeas", "pumpkin"
        )
        val profile = profile(allergies = everything)
        assertTrue(templateMeals(Locale.ENGLISH, profile).isEmpty())
        assertThrows(NoSafeNutritionPlanException::class.java) {
            fallbackNutrition(AiNutritionRequest(profile, weekIndex = 0, locale = "en"))
        }
    }

    @Test
    fun `russian fallback for a vegan with nut allergy is safe`() {
        val profile = profile(allergies = listOf("аллергия на орехи"), dietaryPreferences = listOf("веган"))
        val fallback = fallbackNutrition(AiNutritionRequest(profile, weekIndex = 0, locale = "ru"))
        assertTrue(nutritionSafetyViolations(fallback, profile).isEmpty())
    }

    // ── Chat edit ops (found while fixing this group) ─────────────────────────

    private val applicator = BuiltInExerciseCatalog().let { CoachEditApplicator(it, DefaultTrainingPlanResolver(it)) }

    private fun currentPlan() = AiNutritionResponse(
        weekIndex = 0,
        mealsByDay = mapOf("Mon" to listOf(meal("Rice bowl", "rice", "broccoli"), meal("Oat porridge", "oats", "banana")))
    )

    private fun meal(name: String, vararg ingredients: String) =
        AiMeal(name = name, ingredients = ingredients.toList(), kcal = 500, macros = Macros(30, 15, 60, 500))

    private fun mealOp(op: String, name: String, ingredients: List<String>, mealIndex: Int? = null) = CoachEditOp(
        op = op, day = "Mon", mealIndex = mealIndex, name = name, ingredients = ingredients,
        kcal = 500, proteinGrams = 30, fatGrams = 15, carbsGrams = 60
    )

    @Test
    fun `chat edits cannot introduce a forbidden food`() {
        val forbidden = listOf(
            mealOp("add_meal", "Peanut snack", listOf("peanut butter", "apple")),
            mealOp("swap_meal", "Satay", listOf("chicken", "peanut sauce"), mealIndex = 0),
            CoachEditOp(op = "add_ingredient", day = "Mon", mealIndex = 1, ingredient = "peanut butter"),
            CoachEditOp(op = "set_ingredient", day = "Mon", mealIndex = 1, ingredientIndex = 1, ingredient = "peanuts"),
        )
        for (op in forbidden) {
            val result = applicator.apply(listOf(op), null, currentPlan(), peanutAllergy)
            assertFalse(result.nutritionChanged, op.op)
            assertTrue(result.rejections.single().contains("must avoid"), result.rejections.toString())
        }
    }

    @Test
    fun `safe chat edits still apply for a user with restrictions`() {
        val result = applicator.apply(
            listOf(
                mealOp("add_meal", "Apple snack", listOf("apple", "pumpkin seeds")),
                CoachEditOp(op = "add_ingredient", day = "Mon", mealIndex = 0, ingredient = "carrot"),
            ),
            null, currentPlan(), peanutAllergy
        )
        assertTrue(result.nutritionChanged)
        assertTrue(result.rejections.isEmpty(), result.rejections.toString())
        assertEquals(3, result.nutritionPlan!!.mealsByDay.getValue("Mon").size)
    }

    private fun plan(
        profile: AiProfile,
        ingredient: String = "rice",
        recipe: String = "Serve with vegetables",
        shoppingList: List<String> = emptyList(),
        allergenTags: List<String> = emptyList(),
    ) = AiNutritionResponse(
        weekIndex = 0,
        mealsByDay = days.associateWith { day ->
            (1..3).map { index ->
                AiMeal(
                    name = "$day meal $index ${profile.goal}",
                    ingredients = listOf(ingredient, "vegetables $day $index"),
                    kcal = 700,
                    macros = Macros(50, 20, 80, 700),
                    allergenTags = allergenTags,
                    recipe = recipe
                )
            }
        },
        shoppingList = shoppingList
    )

    private fun profile(
        goal: String = "MAINTAIN",
        allergies: List<String> = emptyList(),
        dietaryPreferences: List<String> = emptyList(),
    ) = AiProfile(
        age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = goal, experienceLevel = 1,
        allergies = allergies, dietaryPreferences = dietaryPreferences
    )
}

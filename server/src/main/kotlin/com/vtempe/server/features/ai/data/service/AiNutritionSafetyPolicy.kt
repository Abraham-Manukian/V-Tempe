package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.nutrition.AiMeal
import com.vtempe.server.shared.dto.nutrition.AiNutritionResponse
import com.vtempe.server.shared.dto.profile.AiProfile
import java.util.Locale

internal enum class FoodRestrictionTag {
    Dairy,
    Lactose,
    Peanut,
    TreeNut,
    Gluten,
    Soy,
    Egg,
    Fish,
    Shellfish,
    Sesame,
    Meat,
    Poultry,
    Honey
}

internal data class NutritionRestrictions(
    val tags: Set<FoodRestrictionTag> = emptySet(),
    val customTerms: Set<String> = emptySet(),
    val allowLactoseFreeAlternatives: Boolean = false
) {
    val isEmpty: Boolean
        get() = tags.isEmpty() && customTerms.isEmpty()

    companion object {
        val None = NutritionRestrictions()
    }
}

private data class MealFrequencyRange(val min: Int, val max: Int)

private val qualityDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
private const val maxWeeklyMealNameRepeats = 4
private const val maxValidationErrors = 12

private val allergyWords = setOf(
    "allergy",
    "allergic",
    "intolerance",
    "intolerant",
    "allergia",
    "аллерг",
    "неперенос"
)

private val dietaryStopWords = setOf(
    "allergy",
    "allergies",
    "allergic",
    "intolerance",
    "intolerant",
    "avoid",
    "without",
    "exclude",
    "allergen",
    "sensitivity",
    "аллергия",
    "аллергии",
    "непереносимость",
    "исключить",
    "избегать",
    "без",
    "на",
    "к",
    "и"
)

private val tagKeywords: Map<FoodRestrictionTag, Set<String>> = mapOf(
    FoodRestrictionTag.Dairy to setOf(
        "milk", "dairy", "cream", "cheese", "yogurt", "kefir", "curd", "casein", "whey",
        "молок", "сливк", "сыр", "йогурт", "кефир", "творог", "сметан", "казеин", "сыворот"
    ),
    FoodRestrictionTag.Lactose to setOf(
        "lactose", "milk", "cream", "whey", "casein", "йогурт", "кефир", "творог", "молок", "лактоз"
    ),
    FoodRestrictionTag.Peanut to setOf("peanut", "арахис", "groundnut"),
    FoodRestrictionTag.TreeNut to setOf("almond", "walnut", "cashew", "hazelnut", "pecan", "nut", "орех", "миндал"),
    FoodRestrictionTag.Gluten to setOf("gluten", "wheat", "barley", "rye", "pasta", "bread", "flour", "глютен", "пшен", "рож", "ячмен", "мук", "хлеб", "макарон"),
    FoodRestrictionTag.Soy to setOf("soy", "soya", "tofu", "соя", "тофу"),
    FoodRestrictionTag.Egg to setOf("egg", "eggs", "яйц"),
    FoodRestrictionTag.Fish to setOf("fish", "salmon", "tuna", "cod", "рыб", "лосос", "тун", "треск"),
    FoodRestrictionTag.Shellfish to setOf("shrimp", "prawn", "mussel", "clam", "lobster", "oyster", "кревет", "мид", "устриц", "омар", "ракообраз"),
    FoodRestrictionTag.Sesame to setOf("sesame", "tahini", "кунжут", "тахин"),
    FoodRestrictionTag.Meat to setOf("meat", "beef", "pork", "lamb", "veal", "мяс", "говя", "свин", "барани", "телят"),
    FoodRestrictionTag.Poultry to setOf("poultry", "chicken", "turkey", "duck", "птиц", "куриц", "курин", "индейк", "утк"),
    // "мед" alone deliberately excluded — substring-matches unrelated words like "медленно" (slowly).
    FoodRestrictionTag.Honey to setOf("honey", "мёд", "медов")
)

private val lactoseFreeMarkers = setOf(
    "lactose-free",
    "lactose free",
    "безлактоз",
    "без лактоз",
    "\u0431\u0435\u0437\u043b\u0430\u043a\u0442\u043e\u0437",
    "\u0431\u0435\u0437 \u043b\u0430\u043a\u0442\u043e\u0437"
)
/**
 * General-purpose negation detector for allergen keywords.
 *
 * Returns true when the allergen keyword (found at [matchIdx], [matchLength] chars long, in
 * [text]) is preceded within [windowChars] characters by a negation word ("без" / "no" /
 * "without"), with NO positive connector (" с " / " with " / comma) appearing between that
 * negation and the keyword — or when the keyword itself carries a free-from label.
 *
 * Returns true  (allergen-FREE → do NOT flag):
 *   "без орехов"                             "без" directly before "орех"
 *   "без молока и орехов"                    "без" before "орех", no "с" in between
 *   "Изолят протеина (без молока и орехов)"  same
 *   "protein bar without nuts and dairy"     "without" before "nuts"
 *   "gluten-free bread"                      free-from label attached to the keyword
 *   "non-dairy creamer", "vegan cheese"      label directly before the keyword
 *
 * Returns false (allergen IS present → flag violation):
 *   "каша без сахара с орехами"  "с" between "без" and "орех" breaks negation context
 *   "грецкие орехи с мёдом"      no negation before "орех"
 *   "молоко 3.2%"                no negation at all
 *   "gluten-free bread with peanut butter"   the label belongs to "gluten", not to "peanut"
 */
private fun isKeywordNegatedInContext(
    text: String,
    matchIdx: Int,
    matchLength: Int,
    windowChars: Int = 48
): Boolean {
    val contextBefore = text.substring((matchIdx - windowChars).coerceAtLeast(0), matchIdx)

    // Russian "без" (without)
    val bezIdx = contextBefore.lastIndexOf("без")
    if (bezIdx != -1) {
        val segment = contextBefore.substring(bezIdx)
        if (!segment.contains(" с ") && !segment.contains(",")) return true
    }

    // English "without" / "no "
    for (negWord in listOf("without ", "no ")) {
        val negIdx = contextBefore.lastIndexOf(negWord)
        if (negIdx != -1) {
            val segment = contextBefore.substring(negIdx)
            if (!segment.contains(" with ") && !segment.contains(",")) return true
        }
    }

    // Free-from labels count only when attached to THIS keyword ("peanut-free", "nut free",
    // "non-dairy", "vegan cheese"). A label elsewhere in the text says nothing about it.
    val wordEnd = text.indexOfAny(charArrayOf(' ', ',', ';', '(', ')'), matchIdx + matchLength)
        .let { if (it == -1) text.length else it }
    val restOfWord = text.substring(matchIdx + matchLength, wordEnd)
    if (restOfWord.endsWith("free") || text.substring(wordEnd).trimStart().startsWith("free")) return true
    val previous = previousWord(text, matchIdx)
    // Exactly "non-"/"non" (as in "non-dairy"): "non-fat milk" is still milk.
    return previous == "non-" || previous == "non" || previous.startsWith("vegan") || previous.startsWith("веган") ||
        previous.startsWith("plant-based")
}

/** The word (or word part, as in "non-" of "non-dairy") directly before [matchIdx]. */
private fun previousWord(text: String, matchIdx: Int): String {
    val before = text.substring(0, matchIdx).trimEnd()
    return before.substring(before.indexOfLast { it == ' ' || it in ",;()" } + 1)
}

/**
 * Words that turn the dairy keyword RIGHT AFTER them into a plant-based alternative:
 * "oat milk", "coconut cream", "овсяное молоко", "кокосовым молоком". Matched per occurrence
 * (see [isPlantDairyAlternativeAt]) — "рисовая лапша с сыром" still contains cheese.
 */
private val plantDairyAlternativePrefixes = listOf(
    "almond", "soy", "oat", "coconut", "rice", "plant", "vegan", "cashew",
    // Russian roots match every grammatical case: "кокосовое/кокосовым", "овсяное/овсяным", …
    "кокосов", "растительн", "овсян", "миндальн", "рисов", "соев", "орехов"
)

/** True when the word directly before the dairy keyword at [matchIdx] names a plant base or
 *  a dairy-free label ("oat milk", "non-dairy creamer", "кокосовым молоком"). */
private fun isPlantDairyAlternativeAt(text: String, matchIdx: Int): Boolean {
    val previous = previousWord(text, matchIdx)
    return plantDairyAlternativePrefixes.any { previous.startsWith(it) } ||
        previous in setOf("dairy-free", "milk-free", "non-dairy")
}

internal fun buildNutritionRestrictions(profile: AiProfile): NutritionRestrictions {
    val allergyLines = (profile.allergies + profile.healthNotes)
        .map(::canonicalText)
        .filter { it.isNotBlank() }
    val preferenceLines = profile.dietaryPreferences
        .map(::canonicalText)
        .filter { it.isNotBlank() }
    if (allergyLines.isEmpty() && preferenceLines.isEmpty()) return NutritionRestrictions.None

    val tags = mutableSetOf<FoodRestrictionTag>()
    val customTerms = mutableSetOf<String>()

    var lactoseMentioned = false
    var dairyAllergy = false

    allergyLines.forEach { line ->
        if (containsAny(line, setOf("lactose", "лактоз"))) lactoseMentioned = true
        if (
            (containsAny(line, setOf("milk", "dairy", "молок", "казеин", "сыворот")) && containsAny(line, allergyWords)) ||
            containsAny(line, setOf("casein", "whey", "казеин", "сыворот"))
        ) {
            dairyAllergy = true
        }
        tags += inferTags(line)
        customTerms += extractCustomRestrictionTerms(line)
    }

    preferenceLines.forEach { line ->
        // NOTE: do NOT call inferTags() here — dietary preferences express what the user LIKES,
        // not what they're allergic to. inferTags("мясо, макароны") would wrongly add Meat
        // to the restriction set, causing the system to forbid meat for someone who enjoys it.
        // Only explicit diet-pattern keywords drive tag inference from preferences.
        if (containsAny(line, setOf("vegan", "веган"))) {
            tags += setOf(
                FoodRestrictionTag.Dairy,
                FoodRestrictionTag.Egg,
                FoodRestrictionTag.Fish,
                FoodRestrictionTag.Shellfish,
                FoodRestrictionTag.Meat,
                FoodRestrictionTag.Poultry,
                FoodRestrictionTag.Honey
            )
        }
        if (containsAny(line, setOf("vegetarian", "вегетариан"))) {
            tags += setOf(
                FoodRestrictionTag.Fish,
                FoodRestrictionTag.Shellfish,
                FoodRestrictionTag.Meat,
                FoodRestrictionTag.Poultry
            )
        }
    }

    if (lactoseMentioned) {
        tags += FoodRestrictionTag.Lactose
        if (dairyAllergy) tags += FoodRestrictionTag.Dairy
    }

    val allowLactoseFree = lactoseMentioned && !dairyAllergy

    return NutritionRestrictions(
        tags = tags,
        customTerms = customTerms,
        allowLactoseFreeAlternatives = allowLactoseFree
    )
}

private val tagForbiddenDescription: Map<FoodRestrictionTag, String> = mapOf(
    FoodRestrictionTag.Meat     to "MEAT (beef, pork, lamb, veal, говядина, свинина, баранина, телятина)",
    FoodRestrictionTag.Poultry  to "POULTRY (chicken, turkey, duck, курица, индейка, утка)",
    FoodRestrictionTag.Dairy    to "DAIRY (milk, cream, butter, cheese, yogurt, kefir, curd, whey, casein — молоко, сливки, масло, сыр, йогурт, кефир, творог, сметана)",
    FoodRestrictionTag.Lactose  to "LACTOSE (milk, yogurt, cream, kefir, curd — молоко, йогурт, кефир, творог; lactose-free alternatives are OK if noted)",
    FoodRestrictionTag.Egg      to "EGGS (egg, яйцо, яйца)",
    FoodRestrictionTag.Fish     to "FISH (fish, salmon, tuna, cod — рыба, лосось, тунец, треска)",
    FoodRestrictionTag.Shellfish to "SHELLFISH (shrimp, prawn, lobster, mussel, oyster — креветки, мидии, устрицы, омар)",
    FoodRestrictionTag.Gluten   to "GLUTEN (wheat, barley, rye, bread, pasta, flour — пшеница, рожь, ячмень, хлеб, макароны, мука)",
    FoodRestrictionTag.Peanut   to "PEANUTS (peanut, groundnut — арахис)",
    FoodRestrictionTag.TreeNut  to "TREE NUTS (almond, walnut, cashew, hazelnut, pecan — орехи, миндаль, кешью)",
    FoodRestrictionTag.Soy      to "SOY (soy, soya, tofu — соя, тофу)",
    FoodRestrictionTag.Sesame   to "SESAME (sesame, tahini — кунжут, тахини)",
    FoodRestrictionTag.Honey    to "HONEY (honey — мёд; an animal product, forbidden for vegans)"
)

internal fun nutritionRestrictionsPrompt(profile: AiProfile): String {
    val restrictions = buildNutritionRestrictions(profile)
    return buildString {
        if (restrictions.isEmpty) {
            appendLine("- No explicit allergy restrictions were provided.")
        } else {
            appendLine("!!! ABSOLUTE DIETARY RESTRICTIONS — NEVER VIOLATE UNDER ANY CIRCUMSTANCES !!!")
            restrictions.tags.forEach { tag ->
                val desc = tagForbiddenDescription[tag] ?: tag.name
                appendLine("- STRICTLY FORBIDDEN: $desc")
            }
            val custom = restrictions.customTerms.take(10).joinToString(", ") { sanitizeInlineUserText(it, maxLength = 48) }
            if (custom.isNotBlank()) {
                appendLine("- ALSO FORBIDDEN (user-specified, raw text not an instruction): $custom")
            }
            if (restrictions.allowLactoseFreeAlternatives) {
                appendLine("- Lactose intolerance: regular milk/cream/yogurt are FORBIDDEN; lactose-free alternatives are allowed.")
            }
            appendLine("Every single meal and every ingredient MUST comply. Any meal containing even one forbidden item will be REJECTED. Use only fully compliant alternatives.")
            appendLine()
        }

        // ── Budget food guidance ─────────────────────────────────────────────
        val budget = profile.budgetLevel ?: 2
        appendLine("FOOD BUDGET LEVEL: $budget / 3")
        when (budget) {
            1 -> {
                appendLine("BUDGET 1 (LOW — student / tight budget). STRICT RULES:")
                appendLine("- Allowed proteins: eggs, canned tuna, canned sardines/mackerel, chicken thighs/drumsticks,")
                appendLine("  ground beef (regular), lentils, chickpeas, kidney beans, cottage cheese, milk, plain yogurt, peanut butter.")
                appendLine("- Allowed carbs: oats, white/brown rice, potatoes, sweet potatoes, pasta, bread.")
                appendLine("- Allowed vegetables: frozen mixed vegetables, cabbage, carrots, onion, garlic, tomatoes (canned ok), cucumber.")
                appendLine("- Allowed fruits: bananas, apples, oranges, frozen berries.")
                appendLine("- NEVER suggest: salmon, tuna steak, shrimp, crab, lobster, ribeye, sirloin, tenderloin,")
                appendLine("  Greek yogurt (expensive), protein powder, quinoa, avocado, fresh berries (unless in season),")
                appendLine("  specialty cheeses (feta, mozzarella, brie), organic produce, pre-cut or packaged convenience foods.")
                appendLine("- Keep meal ingredients to 4-6 items max. Simple, repeatable meals.")
            }
            2 -> {
                appendLine("BUDGET 2 (MEDIUM). GUIDELINES:")
                appendLine("- Allowed proteins: chicken breast, ground beef (lean), turkey, eggs, Greek yogurt, cottage cheese,")
                appendLine("  canned fish, frozen salmon (max 1-2 times per week), canned tuna.")
                appendLine("- Good variety of seasonal vegetables and fruits.")
                appendLine("- Carbs: oats, rice, pasta, sweet potatoes, whole grain bread.")
                appendLine("- Avoid: daily salmon or steak, premium specialty items, exotic superfoods.")
                appendLine("- Fresh salmon ok 1-2x/week max. No premium cuts of beef more than once/week.")
            }
            else -> {
                appendLine("BUDGET 3 (HIGH). Full variety allowed:")
                appendLine("- Salmon, steak, shrimp, premium proteins, fresh seasonal produce, specialty items freely.")
                appendLine("- Optimize for nutrition quality and taste variety.")
            }
        }
        appendLine()

        // ── Allergen tagging ────────────────────────────────────────────────
        appendLine("ALLERGEN TAGGING — MANDATORY:")
        appendLine("For EVERY meal object, include an \"allergenTags\" array listing which of the following allergens are present:")
        appendLine("  ${FoodRestrictionTag.entries.joinToString(", ") { it.name }}")
        appendLine("Rules:")
        appendLine("- If a meal contains milk, yogurt, cheese, cream → include \"Dairy\"")
        appendLine("- If a meal contains any nut (almond, walnut, cashew, etc.) → include \"TreeNut\"")
        appendLine("- If a meal contains gluten/wheat/bread/pasta → include \"Gluten\"")
        appendLine("- Use the ENGLISH tag names exactly as listed above.")
        appendLine("- If no allergens are present → output an empty array: \"allergenTags\": []")
        appendLine("- This tagging must be accurate regardless of the language you are writing the meal names in.")
        append("Example: {\"name\":\"Овсянка с молоком\",\"ingredients\":[\"овсяные хлопья\",\"молоко\"],\"allergenTags\":[\"Dairy\",\"Gluten\"],\"kcal\":300,\"macros\":{...}}")
    }
}

/**
 * Builds a directive, actionable fix instruction for a severe (>20%) calorie miss — computed
 * scaling factor and exact kcal gap, not just "increase portions". The vaguer original wording
 * ("increase portion sizes to reach the target") let the model make token-sized adjustments that
 * never closed a large gap even after 3 retries (e.g. a bulking profile stuck ~30% under target).
 * Telling the model the exact multiplier to apply to ingredient quantities gives it concrete
 * arithmetic to perform instead of a vague direction to guess at.
 */
private fun buildSevereCalorieFixInstruction(day: String, dayKcal: Int, targetKcal: Int): String {
    val delta = targetKcal - dayKcal
    val absDelta = kotlin.math.abs(delta)
    val multiplier = targetKcal.toDouble() / dayKcal.coerceAtLeast(1)
    val multiplierStr = String.format(Locale.US, "%.2f", multiplier)
    val action = if (delta > 0) {
        "you are $absDelta kcal UNDER target — multiply the quantity (grams/ml/pieces) of every " +
            "ingredient in $day's meals by approximately ${multiplierStr}x, or add one more meal " +
            "worth about $absDelta kcal, to close the gap"
    } else {
        "you are $absDelta kcal OVER target — multiply the quantity of every ingredient in $day's " +
            "meals by approximately ${multiplierStr}x to reduce portions and close the gap"
    }
    return "$day calorie sum severe deviation: $dayKcal kcal vs target $targetKcal kcal — $action. " +
        "Do NOT just swap ingredient names — scale the actual gram/ml/piece amounts and recompute " +
        "each meal's kcal/macros to match the new quantities."
}

internal fun validateNutritionPlanQuality(
    plan: AiNutritionResponse,
    profile: AiProfile,
    locale: Locale
): List<String> {
    // Safety first and in full (VT-AUD-027): quality warnings and the report-size cap below
    // must never decide whether the allergen scan runs or whether its findings survive.
    val safetyErrors = nutritionSafetyViolations(plan, buildNutritionRestrictions(profile))
    val errors = mutableListOf<String>()
    val frequencyRange = goalMealRange(profile.goal)

    // Pre-computed daily calorie target — used to flag (non-critically) days whose meals
    // sum more than ±10% off target. Logged as a warning; never crashes generation.
    val targetKcal = computeTargetNutrition(profile).kcal

    qualityDays.forEach { day ->
        val meals = plan.mealsByDay[day].orEmpty()
        if (meals.size !in frequencyRange.min..frequencyRange.max) {
            errors += "$day meal frequency ${meals.size} outside goal range ${frequencyRange.min}-${frequencyRange.max} meals/day"
        }

        if (targetKcal > 0 && meals.isNotEmpty()) {
            val dayKcal = meals.sumOf { meal ->
                if (meal.kcal > 0) meal.kcal
                else computeKcal(meal.macros.proteinGrams, meal.macros.carbsGrams, meal.macros.fatGrams)
            }
            val softLower = (targetKcal * 0.90).toInt()
            val softUpper = (targetKcal * 1.10).toInt()
            // Beyond ±20% the plan is no longer just imprecise — it actively works against the
            // user's goal (e.g. a GAIN_MUSCLE plan landing ~30% under target for a severely
            // underweight profile). That's promoted to a CRITICAL error (see
            // AiQualityErrorPolicy.isNonCriticalError) so it forces a retry with the exact
            // target fed back to the model, instead of silently shipping a plan that fights
            // the user's goal. ±10-20% stays a soft, non-blocking warning.
            val hardLower = (targetKcal * 0.80).toInt()
            val hardUpper = (targetKcal * 1.20).toInt()
            when {
                dayKcal < hardLower || dayKcal > hardUpper ->
                    errors += buildSevereCalorieFixInstruction(day, dayKcal, targetKcal)
                dayKcal < softLower || dayKcal > softUpper ->
                    errors += "$day calorie sum $dayKcal kcal outside ±10% of target $targetKcal kcal"
            }
        }

        val duplicatesInDay = meals
            .groupBy(::mealFingerprint)
            .filterValues { it.size > 1 }
            .values
            .flatten()
            .map { sanitizeText(it.name) }
            .distinct()
        if (duplicatesInDay.isNotEmpty()) {
            errors += "$day contains duplicate meals: ${duplicatesInDay.joinToString(", ")}"
        }
    }

    val repeatedDaySignatures = qualityDays
        .associateWith { day ->
            plan.mealsByDay[day].orEmpty().joinToString("||") { meal ->
                val name = normalizeMealName(meal.name)
                val ingredients = meal.ingredients
                    .map(::canonicalText)
                    .filter { it.isNotBlank() }
                    .joinToString("|")
                "$name::$ingredients"
            }
        }
        .filterValues { it.isNotBlank() }
        .entries
        .groupBy({ it.value }, { it.key })
        .values
        .filter { it.size > 2 }

    if (repeatedDaySignatures.isNotEmpty()) {
        val summary = repeatedDaySignatures
            .take(3)
            .joinToString("; ") { days -> days.joinToString(", ") }
        errors += "identical daily meal plan repeated across too many days: $summary"
    }

    val allMeals = plan.mealsByDay.values.flatten()
    val repeatedNames = allMeals
        .groupingBy { normalizeMealName(it.name) }
        .eachCount()
        .filter { (name, count) -> name.isNotBlank() && count > maxWeeklyMealNameRepeats }
    if (repeatedNames.isNotEmpty()) {
        val summary = repeatedNames.entries
            .sortedByDescending { it.value }
            .take(4)
            .joinToString(", ") { "${it.key} x${it.value}" }
        errors += "meal names repeated too often across week: $summary"
    }

    if (locale.language.equals("ru", ignoreCase = true) && allMeals.isNotEmpty()) {
        val cyrillicMealNames = allMeals.count { hasCyrillic(it.name) }
        val minRequired = ((allMeals.size * 6) + 9) / 10
        if (cyrillicMealNames < minRequired) {
            errors += "nutrition language mismatch: expected Russian meal names in Cyrillic"
        }
    }

    // Only the length of the feedback is capped; safety findings come first, so any plan with
    // a forbidden food always carries at least one critical error.
    return (safetyErrors + errors).distinct().take(maxValidationErrors)
}

/**
 * Makes the fixed fallback templates comply with the user's restrictions (VT-AUD-028): a
 * forbidden ingredient is swapped only for a candidate that is itself allowed, a meal with no
 * allowed ingredient left is dropped, and every returned meal passes the safety gate. The
 * result may be empty — callers must treat that as "no safe fallback", never fill it back in.
 */
internal fun sanitizeTemplateMealsForRestrictions(
    meals: List<AiMeal>,
    profile: AiProfile?,
    locale: Locale
): List<AiMeal> {
    if (profile == null || meals.isEmpty()) return meals
    val restrictions = buildNutritionRestrictions(profile)
    if (restrictions.isEmpty) return meals

    val replacements = if (locale.language.equals("ru", ignoreCase = true)) {
        listOf("рис", "киноа", "овощи", "фрукты", "нут", "семена тыквы")
    } else {
        listOf("rice", "quinoa", "vegetables", "fruit", "chickpeas", "pumpkin seeds")
    }.filter { restrictionViolationReason(it, restrictions) == null }
    val safeNames = if (locale.language.equals("ru", ignoreCase = true)) {
        listOf("Сбалансированный завтрак", "Сбалансированный обед", "Сбалансированный ужин", "Перекус")
    } else {
        listOf("Balanced Breakfast", "Balanced Lunch", "Balanced Dinner", "Snack")
    }

    var replacementIndex = 0
    return meals.mapIndexedNotNull { mealIndex, meal ->
        val safeIngredients = meal.ingredients.mapNotNull { ingredient ->
            when {
                restrictionViolationReason(ingredient, restrictions) == null -> ingredient
                replacements.isEmpty() -> null
                else -> replacements[replacementIndex++ % replacements.size]
            }
        }.filter { it.isNotBlank() }.distinct()
        if (safeIngredients.isEmpty()) return@mapIndexedNotNull null

        val safeName = if (restrictionViolationReason(meal.name, restrictions) != null) {
            safeNames[mealIndex % safeNames.size]
        } else {
            meal.name
        }

        meal.copy(name = safeName, ingredients = safeIngredients)
    }.filter { mealSafetyViolations(it, restrictions).isEmpty() }
}

private fun goalMealRange(goalRaw: String): MealFrequencyRange {
    val goal = goalRaw.trim().uppercase(Locale.US)
    return when {
        goal.contains("LOSE") || goal.contains("FAT") -> MealFrequencyRange(min = 3, max = 4)
        goal.contains("GAIN") || goal.contains("MUSCLE") -> MealFrequencyRange(min = 4, max = 6)
        else -> MealFrequencyRange(min = 3, max = 5)
    }
}

private fun normalizeMealName(raw: String): String =
    canonicalText(raw)
        .replace(Regex("""[^a-zа-я0-9\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()

private fun mealFingerprint(meal: AiMeal): String {
    val name = normalizeMealName(meal.name)
    val ingredients = meal.ingredients
        .map(::normalizeMealName)
        .sorted()
        .joinToString("|")
    return "$name#$ingredients"
}

private fun inferTags(line: String): Set<FoodRestrictionTag> {
    val tags = mutableSetOf<FoodRestrictionTag>()
    if (containsAny(line, setOf("peanut", "арахис"))) tags += FoodRestrictionTag.Peanut
    if (containsAny(line, setOf("nut", "орех", "миндал", "кешью", "hazelnut", "walnut"))) tags += FoodRestrictionTag.TreeNut
    if (containsAny(line, setOf("gluten", "глютен", "целиак", "wheat", "пшен"))) tags += FoodRestrictionTag.Gluten
    if (containsAny(line, setOf("soy", "soya", "соя"))) tags += FoodRestrictionTag.Soy
    if (containsAny(line, setOf("egg", "яйц"))) tags += FoodRestrictionTag.Egg
    if (containsAny(line, setOf("fish", "рыб", "лосос", "тунец", "треск"))) tags += FoodRestrictionTag.Fish
    if (containsAny(line, setOf("shellfish", "shrimp", "prawn", "кревет", "моллюск", "мид"))) tags += FoodRestrictionTag.Shellfish
    if (containsAny(line, setOf("sesame", "кунжут", "тахин"))) tags += FoodRestrictionTag.Sesame
    if (containsAny(line, setOf("dairy", "milk allergy", "молоч", "казеин", "сыворот"))) tags += FoodRestrictionTag.Dairy
    if (containsAny(line, setOf("lactose", "лактоз"))) tags += FoodRestrictionTag.Lactose
    if (containsAny(line, setOf("meat", "beef", "pork", "lamb", "мяс", "говя", "свин", "барани", "телят"))) tags += FoodRestrictionTag.Meat
    if (containsAny(line, setOf("poultry", "chicken", "turkey", "duck", "птиц", "куриц", "индейк", "утк"))) tags += FoodRestrictionTag.Poultry
    return tags
}

private fun extractCustomRestrictionTerms(line: String): Set<String> {
    val out = linkedSetOf<String>()
    val compact = line.trim()
    if (compact.length in 4..48 && !containsAny(compact, allergyWords)) {
        out += compact
    }
    compact
        .split(Regex("""[,\;/|]+|\s+"""))
        .map { it.trim() }
        .filter { it.length >= 4 }
        .filterNot { it in dietaryStopWords }
        .forEach(out::add)
    return out
}

private fun canonicalText(raw: String): String = sanitizeText(raw).lowercase(Locale.US)

private fun containsAny(text: String, needles: Set<String>): Boolean =
    needles.any { needle -> text.contains(needle) }

/** Safe words that merely contain a forbidden keyword: buckwheat is gluten-free, eggplant has
 *  no egg, "мороженое"/"дрожжи" are not rye. */
private val safeCompounds: Map<String, Set<String>> = mapOf(
    "wheat" to setOf("buckwheat"),
    "egg" to setOf("eggplant"),
    "nut" to setOf("nutmeg", "butternut"),
    "рож" to setOf("морож", "дрожж")
)

/** Every start index of [needle] in [text], except inside a known safe compound word. */
private fun occurrencesOf(text: String, needle: String): Sequence<Int> =
    generateSequence(text.indexOf(needle).takeIf { it >= 0 }) { prev ->
        text.indexOf(needle, prev + 1).takeIf { it >= 0 }
    }.filterNot { idx ->
        safeCompounds[needle].orEmpty().any { compound ->
            val offset = compound.indexOf(needle)
            idx - offset >= 0 && text.startsWith(compound, idx - offset)
        }
    }

/**
 * Why [raw] breaks [restrictions], or null when it complies. Every occurrence of every forbidden
 * keyword/term is examined: a negated or plant-based first mention ("peanut-free granola") must
 * not hide a later real one ("… with peanut butter").
 */
internal fun restrictionViolationReason(raw: String, restrictions: NutritionRestrictions): String? {
    val text = canonicalText(raw)
    if (text.isBlank()) return null

    restrictions.customTerms.forEach { term ->
        if (term.isBlank()) return@forEach
        // Dairy/lactose custom terms are not violated by a plant-based alternative
        // e.g. user has "молоко" allergy but "миндальное молоко" / "растительное молоко" is fine
        val isDairyTerm = tagKeywords[FoodRestrictionTag.Dairy].orEmpty().any { term.contains(it) } ||
            tagKeywords[FoodRestrictionTag.Lactose].orEmpty().any { term.contains(it) }

        val lactoseTerm = term.contains("lactos") || term.contains("лактоз")
        val allowLactoseFreeTerm = restrictions.allowLactoseFreeAlternatives &&
            lactoseTerm && lactoseFreeMarkers.any { text.contains(it) }
        if (allowLactoseFreeTerm) return@forEach

        // Skip occurrences negated in context ("без молока", "no milk", etc.)
        val present = occurrencesOf(text, term).any { idx ->
            !isKeywordNegatedInContext(text, idx, term.length) &&
                !(isDairyTerm && isPlantDairyAlternativeAt(text, idx))
        }
        if (present) return "forbidden term '$term' in '$raw'"
    }

    restrictions.tags.forEach { tag ->
        val isDairyFamilyTag = tag == FoodRestrictionTag.Lactose || tag == FoodRestrictionTag.Dairy
        val lactoseFreeOk = restrictions.allowLactoseFreeAlternatives &&
            tag == FoodRestrictionTag.Lactose &&
            lactoseFreeMarkers.any { text.contains(it) }
        if (lactoseFreeOk) return@forEach

        tagKeywords[tag].orEmpty().forEach { keyword ->
            val present = occurrencesOf(text, keyword).any { idx ->
                !isKeywordNegatedInContext(text, idx, keyword.length) &&
                    !(isDairyFamilyTag && isPlantDairyAlternativeAt(text, idx))
            }
            if (present) return "${tag.name} keyword '$keyword' in '$raw'"
        }
    }

    return null
}

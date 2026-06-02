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
    Poultry
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
    FoodRestrictionTag.Poultry to setOf("poultry", "chicken", "turkey", "duck", "птиц", "куриц", "индейк", "утк")
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
 * Returns true when the allergen keyword (found at [matchIdx] in [text]) is preceded within
 * [windowChars] characters by a negation word ("без" / "no" / "without"), with NO positive
 * connector (" с " / " with " / comma) appearing between that negation and the keyword.
 *
 * Returns true  (allergen-FREE → do NOT flag):
 *   "без орехов"                             "без" directly before "орех"
 *   "без молока и орехов"                    "без" before "орех", no "с" in between
 *   "Изолят протеина (без молока и орехов)"  same
 *   "protein bar without nuts and dairy"     "without" before "nuts"
 *   "gluten-free bread"                      explicit free-label
 *
 * Returns false (allergen IS present → flag violation):
 *   "каша без сахара с орехами"  "с" between "без" and "орех" breaks negation context
 *   "грецкие орехи с мёдом"      no negation before "орех"
 *   "молоко 3.2%"                no negation at all
 */
private fun isKeywordNegatedInContext(text: String, matchIdx: Int, windowChars: Int = 48): Boolean {
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

    // Explicit *-free labels anywhere in the full ingredient text
    return text.contains("-free") || text.contains("non-dairy") || text.contains("vegan")
}

private val plantDairyAlternativeMarkers = setOf(
    // English — full phrases
    "almond milk", "soy milk", "oat milk", "coconut milk", "rice milk",
    "plant milk", "vegan milk", "plant cream", "oat cream", "coconut cream",
    "plant yogurt", "coconut yogurt", "soy yogurt", "dairy-free", "non-dairy",
    // Russian — root forms that match all grammatical cases:
    // "кокосов" → кокосовое/кокосовым/кокосовые молоко/молоком/сливки/йогурт
    "кокосов",
    // "растительн" → растительное/растительным/растительные/растительный
    "растительн",
    // "овсян" → овсяное/овсяным/овсяные молоко/сливки
    "овсян",
    // "миндальн" → миндальное/миндальным молоко
    "миндальн",
    // "рисов" → рисовое/рисовым молоко
    "рисов",
    // "соев" → соевое/соевым молоко/йогурт
    "соев",
    // "ореховое молоко" and similar nut milks
    "ореховое молок", "ореховым молок"
)

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
                FoodRestrictionTag.Poultry
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
    FoodRestrictionTag.Sesame   to "SESAME (sesame, tahini — кунжут, тахини)"
)

internal fun nutritionRestrictionsPrompt(profile: AiProfile): String {
    val restrictions = buildNutritionRestrictions(profile)
    if (restrictions.isEmpty) {
        return "- No explicit allergy restrictions were provided."
    }
    return buildString {
        appendLine("!!! ABSOLUTE DIETARY RESTRICTIONS — NEVER VIOLATE UNDER ANY CIRCUMSTANCES !!!")
        restrictions.tags.forEach { tag ->
            val desc = tagForbiddenDescription[tag] ?: tag.name
            appendLine("- STRICTLY FORBIDDEN: $desc")
        }
        val custom = restrictions.customTerms.take(10).joinToString(", ")
        if (custom.isNotBlank()) {
            appendLine("- ALSO FORBIDDEN (user-specified): $custom")
        }
        if (restrictions.allowLactoseFreeAlternatives) {
            appendLine("- Lactose intolerance: regular milk/cream/yogurt are FORBIDDEN; lactose-free alternatives are allowed.")
        }
        appendLine("Every single meal and every ingredient MUST comply. Any meal containing even one forbidden item will be REJECTED. Use only fully compliant alternatives.")
        appendLine()
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

internal fun validateNutritionPlanQuality(
    plan: AiNutritionResponse,
    profile: AiProfile,
    locale: Locale
): List<String> {
    val errors = mutableListOf<String>()
    val restrictions = buildNutritionRestrictions(profile)
    val frequencyRange = goalMealRange(profile.goal)

    qualityDays.forEach { day ->
        val meals = plan.mealsByDay[day].orEmpty()
        if (meals.size !in frequencyRange.min..frequencyRange.max) {
            errors += "$day meal frequency ${meals.size} outside goal range ${frequencyRange.min}-${frequencyRange.max} meals/day"
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

    if (!restrictions.isEmpty) {
        qualityDays.forEach { day ->
            if (errors.size >= maxValidationErrors) return@forEach
            val meals = plan.mealsByDay[day].orEmpty()
            meals.forEachIndexed { mealIndex, meal ->
                if (errors.size >= maxValidationErrors) return@forEachIndexed

                // Primary check: use LLM-provided allergenTags (language-agnostic)
                if (meal.allergenTags.isNotEmpty()) {
                    val forbiddenTags = meal.allergenTags
                        .mapNotNull { tagName ->
                            runCatching { FoodRestrictionTag.valueOf(tagName) }.getOrNull()
                        }
                        .filter { it in restrictions.tags }
                    forbiddenTags.forEach { tag ->
                        errors += "allergen restriction violation in $day meal[$mealIndex]: " +
                            "LLM tagged '${tag.name}' which is forbidden for this user"
                    }
                } else {
                    // Fallback: keyword-based check for old responses without allergenTags
                    val nameViolation = restrictionViolationReason(meal.name, restrictions)
                    if (nameViolation != null) {
                        errors += "allergen restriction violation in $day meal[$mealIndex].name: $nameViolation"
                    }
                    meal.ingredients.forEachIndexed { ingredientIndex, ingredient ->
                        if (errors.size >= maxValidationErrors) return@forEachIndexed
                        val ingredientViolation = restrictionViolationReason(ingredient, restrictions)
                        if (ingredientViolation != null) {
                            errors += "allergen restriction violation in $day meal[$mealIndex].ingredients[$ingredientIndex]: $ingredientViolation"
                        }
                    }
                }
            }
        }
    }

    return errors.distinct().take(maxValidationErrors)
}

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
    }
    val safeNames = if (locale.language.equals("ru", ignoreCase = true)) {
        listOf("Сбалансированный завтрак", "Сбалансированный обед", "Сбалансированный ужин", "Перекус")
    } else {
        listOf("Balanced Breakfast", "Balanced Lunch", "Balanced Dinner", "Snack")
    }

    var replacementIndex = 0
    return meals.mapIndexed { mealIndex, meal ->
        val adjustedIngredients = meal.ingredients.map { ingredient ->
            if (restrictionViolationReason(ingredient, restrictions) == null) {
                ingredient
            } else {
                val replacement = replacements[replacementIndex % replacements.size]
                replacementIndex += 1
                replacement
            }
        }.filter { it.isNotBlank() }.distinct()

        val safeIngredients = when {
            adjustedIngredients.isNotEmpty() -> adjustedIngredients
            else -> listOf(replacements[replacementIndex++ % replacements.size])
        }

        val safeName = if (restrictionViolationReason(meal.name, restrictions) != null) {
            safeNames[mealIndex % safeNames.size]
        } else {
            meal.name
        }

        meal.copy(name = safeName, ingredients = safeIngredients)
    }
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

private fun restrictionViolationReason(raw: String, restrictions: NutritionRestrictions): String? {
    val text = canonicalText(raw)
    if (text.isBlank()) return null

    restrictions.customTerms.forEach { term ->
        if (term.isNotBlank() && text.contains(term)) {
            val termIdx = text.indexOf(term)

            // Skip if the term is negated in context ("\u0431\u0435\u0437 \u043c\u043e\u043b\u043e\u043a\u0430", "no milk", etc.)
            if (isKeywordNegatedInContext(text, termIdx)) return@forEach

            // Skip dairy/lactose custom terms when the ingredient is a plant-based alternative
            // e.g. user has "\u043c\u043e\u043b\u043e\u043a\u043e" allergy but "\u043c\u0438\u043d\u0434\u0430\u043b\u044c\u043d\u043e\u0435 \u043c\u043e\u043b\u043e\u043a\u043e" / "\u0440\u0430\u0441\u0442\u0438\u0442\u0435\u043b\u044c\u043d\u043e\u0435 \u043c\u043e\u043b\u043e\u043a\u043e" is fine
            val isDairyTerm = tagKeywords[FoodRestrictionTag.Dairy].orEmpty().any { term.contains(it) } ||
                tagKeywords[FoodRestrictionTag.Lactose].orEmpty().any { term.contains(it) }
            if (isDairyTerm && plantDairyAlternativeMarkers.any { text.contains(it) }) return@forEach

            val lactoseTerm = term.contains("lactos") || term.contains("\u043b\u0430\u043a\u0442\u043e\u0437")
            val allowLactoseFreeTerm = restrictions.allowLactoseFreeAlternatives &&
                lactoseTerm && lactoseFreeMarkers.any { text.contains(it) }
            if (allowLactoseFreeTerm) return@forEach

            return "forbidden term '$term' in '$raw'"
        }
    }

    restrictions.tags.forEach { tag ->
        val keywords = tagKeywords[tag].orEmpty()
        val match = keywords.firstOrNull { keyword -> text.contains(keyword) } ?: return@forEach
        val matchIdx = text.indexOf(match)

        val isDairyFamilyTag = tag == FoodRestrictionTag.Lactose || tag == FoodRestrictionTag.Dairy
        val isNegated        = isKeywordNegatedInContext(text, matchIdx)
        val hasPlantAlt      = isDairyFamilyTag && plantDairyAlternativeMarkers.any { text.contains(it) }
        val lactoseFreeOk    = restrictions.allowLactoseFreeAlternatives &&
            tag == FoodRestrictionTag.Lactose &&
            lactoseFreeMarkers.any { text.contains(it) }

        if (!lactoseFreeOk && !isNegated && !hasPlantAlt) {
            return "${tag.name} keyword '$match' in '$raw'"
        }
    }

    return null
}


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
    FoodRestrictionTag.Meat to setOf("beef", "pork", "lamb", "veal", "говя", "свин", "барани", "телят"),
    FoodRestrictionTag.Poultry to setOf("chicken", "turkey", "duck", "куриц", "индейк", "утк")
)

private val lactoseFreeMarkers = setOf(
    "lactose-free",
    "lactose free",
    "безлактоз",
    "без лактоз",
    "\u0431\u0435\u0437\u043b\u0430\u043a\u0442\u043e\u0437",
    "\u0431\u0435\u0437 \u043b\u0430\u043a\u0442\u043e\u0437"
)
private val dairyNegationMarkers = setOf(
    "without milk",
    "no milk",
    "without dairy",
    "no dairy",
    "без молока",
    "без молочн",
    "без лактоз"
)
private val plantDairyAlternativeMarkers = setOf(
    "almond milk",
    "soy milk",
    "oat milk",
    "coconut milk",
    "rice milk",
    "plant milk",
    "vegan milk",
    "миндальное молоко",
    "соевое молоко",
    "овсяное молоко",
    "кокосовое молоко",
    "рисовое молоко",
    "растительное молоко"
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
        tags += inferTags(line)
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

internal fun nutritionRestrictionsPrompt(profile: AiProfile): String {
    val restrictions = buildNutritionRestrictions(profile)
    if (restrictions.isEmpty) {
        return "- No explicit allergy restrictions were provided."
    }
    val tags = restrictions.tags.joinToString(", ") { it.name }
    val custom = restrictions.customTerms.take(10).joinToString(", ")
    return buildString {
        appendLine("- HARD FOOD RESTRICTIONS: $tags")
        if (custom.isNotBlank()) {
            appendLine("- User-specific restricted terms: $custom")
        }
        if (restrictions.allowLactoseFreeAlternatives) {
            appendLine("- Lactose intolerance detected: lactose-free alternatives are allowed, regular lactose ingredients are forbidden.")
        }
        append("- Never include ingredients that violate these restrictions.")
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
            meals.forEachIndexed mealLoop@{ mealIndex, meal ->
                val nameViolation = restrictionViolationReason(meal.name, restrictions)
                if (nameViolation != null) {
                    errors += "allergen restriction violation in $day meal[$mealIndex].name: $nameViolation"
                    if (errors.size >= maxValidationErrors) return@mealLoop
                }
                meal.ingredients.forEachIndexed { ingredientIndex, ingredient ->
                    val ingredientViolation = restrictionViolationReason(ingredient, restrictions)
                    if (ingredientViolation != null) {
                        errors += "allergen restriction violation in $day meal[$mealIndex].ingredients[$ingredientIndex]: $ingredientViolation"
                    }
                    if (errors.size >= maxValidationErrors) return@mealLoop
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
    if (containsAny(line, setOf("beef", "pork", "lamb", "говя", "свин", "баран"))) tags += FoodRestrictionTag.Meat
    if (containsAny(line, setOf("chicken", "turkey", "duck", "куриц", "индейк", "утк"))) tags += FoodRestrictionTag.Poultry
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
            val lactoseTerm =
                term.contains("lactos") || term.contains("\u043b\u0430\u043a\u0442\u043e\u0437")
            val allowLactoseFreeTerm =
                restrictions.allowLactoseFreeAlternatives &&
                    lactoseTerm &&
                    lactoseFreeMarkers.any { marker -> text.contains(marker) }
            if (allowLactoseFreeTerm) return@forEach
            return "forbidden term '$term' in '$raw'"
        }
    }

    restrictions.tags.forEach { tag ->
        val keywords = tagKeywords[tag].orEmpty()
        val match = keywords.firstOrNull { keyword -> text.contains(keyword) }
        if (match != null) {
            val isDairyFamilyTag = tag == FoodRestrictionTag.Lactose || tag == FoodRestrictionTag.Dairy
            val hasNegation = isDairyFamilyTag && dairyNegationMarkers.any { marker -> text.contains(marker) }
            val hasPlantAlternative = isDairyFamilyTag && plantDairyAlternativeMarkers.any { marker -> text.contains(marker) }
            val lactoseFree = restrictions.allowLactoseFreeAlternatives &&
                tag == FoodRestrictionTag.Lactose &&
                lactoseFreeMarkers.any { marker -> text.contains(marker) }
            if (!lactoseFree && !hasNegation && !hasPlantAlternative) {
                return "${tag.name} keyword '$match' in '$raw'"
            }
        }
    }

    return null
}


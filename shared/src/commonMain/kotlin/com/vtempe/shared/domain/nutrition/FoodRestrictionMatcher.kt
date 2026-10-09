package com.vtempe.shared.domain.nutrition

import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan

/**
 * Why [raw] breaks these restrictions, or null when it complies. Client port of the server's
 * `restrictionViolationReason`: every occurrence of every forbidden word is examined, and a
 * negation or free-from label only covers the word it belongs to.
 */
fun FoodRestrictions.violationReason(raw: String): String? {
    val text = canonicalFoodText(raw)
    if (text.isBlank()) return null

    customTerms.forEach { term ->
        if (term.isBlank()) return@forEach
        val isDairyTerm = FoodKeywords.foodTags.getValue(FoodAllergen.Dairy).any { term.contains(it) } ||
            FoodKeywords.foodTags.getValue(FoodAllergen.Lactose).any { term.contains(it) }
        val lactoseTerm = term.contains("lactos") || term.contains("лактоз")
        if (allowLactoseFreeAlternatives && lactoseTerm && text.containsAny(FoodKeywords.lactoseFreeMarkers)) return@forEach
        val present = occurrencesOf(text, term).any { idx ->
            !isNegatedAt(text, idx, term.length) && !(isDairyTerm && isPlantDairyAlternativeAt(text, idx))
        }
        if (present) return "forbidden term '$term' in '$raw'"
    }

    tags.forEach { tag ->
        val isDairyFamily = tag == FoodAllergen.Dairy || tag == FoodAllergen.Lactose
        if (allowLactoseFreeAlternatives && tag == FoodAllergen.Lactose && text.containsAny(FoodKeywords.lactoseFreeMarkers)) {
            return@forEach
        }
        FoodKeywords.foodTags[tag].orEmpty().forEach { keyword ->
            val present = occurrencesOf(text, keyword).any { idx ->
                !isNegatedAt(text, idx, keyword.length) && !(isDairyFamily && isPlantDairyAlternativeAt(text, idx))
            }
            if (present) return "${tag.name} keyword '$keyword' in '$raw'"
        }
    }
    return null
}

/** Location-tagged violations in one meal: name, ingredients and recipe (clause by clause). */
fun FoodRestrictions.mealViolations(meal: Meal): List<String> {
    if (isEmpty) return emptyList()
    val out = mutableListOf<String>()
    violationReason(meal.name)?.let { out += "name: $it" }
    meal.ingredients.forEachIndexed { i, ingredient -> violationReason(ingredient)?.let { out += "ingredients[$i]: $it" } }
    recipeClauses(meal.recipe).forEach { clause -> violationReason(clause)?.let { out += "recipe: $it" } }
    return out
}

/** Every violation in a plan, shopping list included. Empty means safe to show and store. */
fun FoodRestrictions.planViolations(plan: NutritionPlan): List<String> {
    if (isEmpty) return emptyList()
    val out = mutableListOf<String>()
    plan.mealsByDay.forEach { (day, meals) ->
        meals.forEachIndexed { i, meal -> mealViolations(meal).forEach { out += "$day meal[$i].$it" } }
    }
    plan.shoppingList.forEachIndexed { i, item -> violationReason(item)?.let { out += "shoppingList[$i]: $it" } }
    return out
}

private val recipeClauseBreak = Regex("""[.!?;:\n]+|\s+(?:and|then|и|затем|потом|после)\s+""")

private fun recipeClauses(recipe: String): List<String> =
    recipe.lowercase().split(recipeClauseBreak).map { it.trim() }.filter { it.isNotEmpty() }

/** Every start index of [needle] in [text], except inside a known safe compound word. */
private fun occurrencesOf(text: String, needle: String): Sequence<Int> =
    generateSequence(text.indexOf(needle).takeIf { it >= 0 }) { prev ->
        text.indexOf(needle, prev + 1).takeIf { it >= 0 }
    }.filterNot { idx ->
        FoodKeywords.safeCompounds[needle].orEmpty().any { compound ->
            val offset = compound.indexOf(needle)
            idx - offset >= 0 && text.startsWith(compound, idx - offset)
        }
    }

/** "без …"/"without …"/"no …" before the word within its clause, or a free-from label on it. */
private fun isNegatedAt(text: String, matchIdx: Int, matchLength: Int, windowChars: Int = 48): Boolean {
    val before = text.substring((matchIdx - windowChars).coerceAtLeast(0), matchIdx)

    val bezIdx = before.lastIndexOf("без")
    if (bezIdx != -1) {
        val segment = before.substring(bezIdx)
        if (!segment.contains(" с ") && !segment.contains(",")) return true
    }
    for (negation in listOf("without ", "no ")) {
        val negIdx = before.lastIndexOf(negation)
        if (negIdx != -1) {
            val segment = before.substring(negIdx)
            if (!segment.contains(" with ") && !segment.contains(",")) return true
        }
    }

    val wordEnd = text.indexOfAny(charArrayOf(' ', ',', ';', '(', ')'), matchIdx + matchLength)
        .let { if (it == -1) text.length else it }
    val restOfWord = text.substring(matchIdx + matchLength, wordEnd)
    if (restOfWord.endsWith("free") || text.substring(wordEnd).trimStart().startsWith("free")) return true
    val previous = previousWord(text, matchIdx)
    return previous == "non-" || previous == "non" || previous.startsWith("vegan") || previous.startsWith("веган") ||
        previous.startsWith("plant-based")
}

private fun isPlantDairyAlternativeAt(text: String, matchIdx: Int): Boolean {
    val previous = previousWord(text, matchIdx)
    return FoodKeywords.plantDairyPrefixes.any { previous.startsWith(it) } || previous in FoodKeywords.dairyFreeLabels
}

private fun previousWord(text: String, matchIdx: Int): String {
    val before = text.substring(0, matchIdx).trimEnd()
    return before.substring(before.indexOfLast { it == ' ' || it in ",;()" } + 1)
}

package com.vtempe.shared.domain.nutrition

import com.vtempe.shared.domain.model.Profile

/** Allergen / diet categories. Names match the server's `FoodRestrictionTag`. */
enum class FoodAllergen {
    Dairy, Lactose, Peanut, TreeNut, Gluten, Soy, Egg, Fish, Shellfish, Sesame, Meat, Poultry, Honey
}

/**
 * What a user must not eat, parsed from the profile's allergies, health notes and diet.
 *
 * Client port of the server's `buildNutritionRestrictions` (server/.../AiNutritionSafetyPolicy.kt):
 * the same keywords and rules, so an offline plan and a server plan are judged by one contract.
 * Both sides are pinned by the shared fixture `testdata/food-restriction-cases.tsv`.
 */
data class FoodRestrictions(
    val tags: Set<FoodAllergen> = emptySet(),
    val customTerms: Set<String> = emptySet(),
    val allowLactoseFreeAlternatives: Boolean = false,
) {
    val isEmpty: Boolean get() = tags.isEmpty() && customTerms.isEmpty()

    companion object {
        val None = FoodRestrictions()

        fun from(profile: Profile): FoodRestrictions = parse(
            allergies = profile.allergies + profile.constraints.healthNotes,
            dietaryPreferences = profile.dietaryPreferences,
        )

        fun parse(allergies: List<String>, dietaryPreferences: List<String>): FoodRestrictions {
            val allergyLines = allergies.map(::canonicalFoodText).filter { it.isNotBlank() }
            val preferenceLines = dietaryPreferences.map(::canonicalFoodText).filter { it.isNotBlank() }
            if (allergyLines.isEmpty() && preferenceLines.isEmpty()) return None

            val tags = mutableSetOf<FoodAllergen>()
            val customTerms = linkedSetOf<String>()
            var lactoseMentioned = false
            var dairyAllergy = false

            allergyLines.forEach { line ->
                if (line.containsAny(setOf("lactose", "лактоз"))) lactoseMentioned = true
                if ((line.containsAny(setOf("milk", "dairy", "молок", "казеин", "сыворот")) && line.containsAny(FoodKeywords.allergyWords)) ||
                    line.containsAny(setOf("casein", "whey", "казеин", "сыворот"))
                ) {
                    dairyAllergy = true
                }
                tags += inferTags(line)
                customTerms += customTermsOf(line)
            }

            // Preferences express what the user likes; only explicit diet patterns restrict.
            preferenceLines.forEach { line ->
                if (line.containsAny(setOf("vegan", "веган"))) tags += FoodKeywords.veganForbidden
                if (line.containsAny(setOf("vegetarian", "вегетариан"))) tags += FoodKeywords.vegetarianForbidden
            }

            if (lactoseMentioned) {
                tags += FoodAllergen.Lactose
                if (dairyAllergy) tags += FoodAllergen.Dairy
            }
            return FoodRestrictions(
                tags = tags,
                customTerms = customTerms,
                allowLactoseFreeAlternatives = lactoseMentioned && !dairyAllergy,
            )
        }

        private fun inferTags(line: String): Set<FoodAllergen> =
            FoodKeywords.allergyLineTags.filterValues { line.containsAny(it) }.keys

        private fun customTermsOf(line: String): Set<String> {
            val out = linkedSetOf<String>()
            val compact = line.trim()
            if (compact.length in 4..48 && !compact.containsAny(FoodKeywords.allergyWords)) out += compact
            compact.split(Regex("""[,;/|]+|\s+"""))
                .map { it.trim() }
                .filter { it.length >= 4 && it !in FoodKeywords.dietaryStopWords }
                .forEach(out::add)
            return out
        }
    }
}

internal fun canonicalFoodText(raw: String): String = raw.trim().replace(Regex("""\s+"""), " ").lowercase()

internal fun String.containsAny(needles: Collection<String>): Boolean = needles.any { contains(it) }

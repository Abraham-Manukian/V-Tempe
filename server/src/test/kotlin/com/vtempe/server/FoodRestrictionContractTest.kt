package com.vtempe.server

import com.vtempe.server.features.ai.data.service.buildNutritionRestrictions
import com.vtempe.server.features.ai.data.service.restrictionViolationReason
import com.vtempe.server.shared.dto.profile.AiProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** Server side of the shared food-restriction contract; the client runs the same file
 *  (shared FoodRestrictionContractTest), so the two implementations cannot drift apart. */
class FoodRestrictionContractTest {

    @Test
    fun `server matcher agrees with the shared contract cases`() {
        val cases = File("../testdata/food-restriction-cases.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
        assertTrue(cases.size >= 30, "contract file looks truncated: ${cases.size} cases")

        val mismatches = cases.mapNotNull { (allergies, diet, text, expected) ->
            val profile = AiProfile(
                age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 1,
                allergies = allergies.split(';').filter { it.isNotBlank() },
                dietaryPreferences = diet.split(';').filter { it.isNotBlank() },
            )
            val actual = if (restrictionViolationReason(text, buildNutritionRestrictions(profile)) == null) "ok" else "violation"
            "[$allergies|$diet] '$text': expected $expected, got $actual".takeIf { actual != expected }
        }
        assertEquals(emptyList<String>(), mismatches)
    }
}

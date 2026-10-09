package com.vtempe.shared.domain.nutrition

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Client side of the shared food-restriction contract (see server FoodRestrictionContractTest). */
class FoodRestrictionContractTest {

    @Test
    fun clientMatcherAgreesWithTheSharedContractCases() {
        val cases = File("../testdata/food-restriction-cases.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split('\t') }
        assertTrue(cases.size >= 30, "contract file looks truncated: ${cases.size} cases")

        val mismatches = cases.mapNotNull { (allergies, diet, text, expected) ->
            val restrictions = FoodRestrictions.parse(
                allergies = allergies.split(';').filter { it.isNotBlank() },
                dietaryPreferences = diet.split(';').filter { it.isNotBlank() },
            )
            val actual = if (restrictions.violationReason(text) == null) "ok" else "violation"
            "[$allergies|$diet] '$text': expected $expected, got $actual".takeIf { actual != expected }
        }
        assertEquals(emptyList(), mismatches)
    }
}

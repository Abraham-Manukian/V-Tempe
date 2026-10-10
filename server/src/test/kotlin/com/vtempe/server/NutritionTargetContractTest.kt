package com.vtempe.server

import com.vtempe.server.features.ai.data.service.computeTargetNutrition
import com.vtempe.server.features.ai.data.service.fallbackNutrition
import com.vtempe.server.shared.dto.nutrition.AiNutritionRequest
import com.vtempe.server.shared.dto.profile.AiProfile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** VT-AUD-029 (macros fit the calorie target) and VT-AUD-035 (bad measurements never crash),
 *  pinned to the targets the client computes for the same profiles. */
class NutritionTargetContractTest {

    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun profile(sex: String, age: Int, h: Int, w: Double, goal: String, life: String, trainingDays: Int) = AiProfile(
        age = age, sex = sex, heightCm = h, weightKg = w, goal = goal, experienceLevel = 2,
        lifestyleActivity = life, weeklySchedule = days.take(trainingDays).associateWith { true },
    )

    @Test
    fun `server targets match the shared contract`() {
        val rows = File("../testdata/nutrition-target-cases.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        assertTrue(rows.size >= 30)
        val mismatches = rows.mapNotNull { r ->
            val t = computeTargetNutrition(profile(r[0], r[1].toInt(), r[2].toInt(), r[3].toDouble(), r[4], r[5], r[6].toInt()))
            val actual = listOf(t.kcal, t.proteinG, t.fatG, t.carbsG).map(Int::toString)
            "$r -> $actual".takeIf { actual != r.subList(7, 11) }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun `macros never ask for more energy than the target`() {
        val audit = computeTargetNutrition(profile("FEMALE", 65, 150, 120.0, "LOSE_FAT", "SEDENTARY", 0))
        assertEquals(1684, audit.kcal)
        assertTrue(audit.proteinG * 4 + audit.fatG * 9 + audit.carbsG * 4 in (1684 - 12)..1684)

        for (w in listOf(20.0, 45.0, 80.0, 120.0, 200.0, 400.0)) for (goal in listOf("LOSE_FAT", "MAINTAIN", "GAIN_MUSCLE"))
            for (sex in listOf("MALE", "FEMALE")) for (age in listOf(10, 40, 110)) for (d in 0..7) {
                val t = computeTargetNutrition(profile(sex, age, 160, w, goal, "SEDENTARY", d))
                val energy = t.proteinG * 4 + t.fatG * 9 + t.carbsG * 4
                assertTrue(energy <= t.kcal && energy >= t.kcal - 12, "$sex $age $w $goal $d: $t = $energy")
                assertTrue(t.proteinG >= 0 && t.fatG >= 0 && t.carbsG >= 0)
            }
    }

    @Test
    fun `out-of-range measurements are clamped instead of crashing`() {
        for (w in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY, 10_000.0)) {
            val p = profile("MALE", 30, 180, w, "MAINTAIN", "SEDENTARY", 3)
            val t = computeTargetNutrition(p)
            assertTrue(t.kcal > 0, "weight $w")
            assertTrue(fallbackNutrition(AiNutritionRequest(p, 0, "en")).mealsByDay.isNotEmpty())
        }
    }
}

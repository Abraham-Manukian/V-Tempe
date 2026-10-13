package com.vtempe.shared.domain.nutrition

import com.vtempe.shared.data.nutrition.OfflineNutritionPlanner
import com.vtempe.shared.domain.model.dayTotals
import com.vtempe.shared.domain.model.hasNutrition
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.LifestyleActivity
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.ProfileBounds
import com.vtempe.shared.domain.model.Sex
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** VT-AUD-029 and VT-AUD-035 on the client; the same contract file as the server test. */
class EnergyTargetsTest {

    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun profile(sex: String, age: Int, h: Int, w: Double, goal: String, life: String, trainingDays: Int) = Profile(
        id = "p", age = age, sex = Sex.valueOf(sex), heightCm = h, weightKg = w, goal = Goal.valueOf(goal), experienceLevel = 2,
        lifestyleActivity = LifestyleActivity.valueOf(life), weeklySchedule = days.take(trainingDays).associateWith { true },
    )

    @Test
    fun clientTargetsMatchTheSharedContract() {
        val rows = File("../testdata/nutrition-target-cases.tsv").readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() && !it.startsWith("#") }.map { it.split('\t') }
        assertTrue(rows.size >= 30)
        val mismatches = rows.mapNotNull { r ->
            val m = EnergyTargets.forProfile(profile(r[0], r[1].toInt(), r[2].toInt(), r[3].toDouble(), r[4], r[5], r[6].toInt()))
            val actual = listOf(m.kcal, m.proteinGrams, m.fatGrams, m.carbsGrams).map(Int::toString)
            "$r -> $actual".takeIf { actual != r.subList(7, 11) }
        }
        assertEquals(emptyList(), mismatches)
    }

    @Test
    fun offlinePlanStatesTheReportedTargetAndNoInventedMealNumbers() {
        val p = profile("FEMALE", 65, 150, 120.0, "LOSE_FAT", "SEDENTARY", 0)
        val plan = OfflineNutritionPlanner().buildPlan(p, 0, "en")
        val target = checkNotNull(plan.dailyTarget)
        val energy = target.proteinGrams * 4 + target.fatGrams * 9 + target.carbsGrams * 4
        assertEquals(1684, target.kcal)
        assertTrue(energy in (1684 - 12)..1684, "macros give $energy kcal")
        // VT-AUD-036: templates have no portions, so no meal states numbers; totals use the target.
        assertTrue(plan.mealsByDay.values.flatten().none { it.hasNutrition })
        assertEquals(target, plan.dayTotals("Mon"))
    }

    @Test
    fun invalidMeasurementsAreRejectedAndNeverCrashCalculations() {
        assertEquals(setOf(ProfileBounds.Field.WEIGHT), ProfileBounds.invalidFields(30, 180, -1.0))
        assertEquals(setOf(ProfileBounds.Field.WEIGHT), ProfileBounds.invalidFields(30, 180, Double.NaN))
        assertEquals(setOf(ProfileBounds.Field.AGE, ProfileBounds.Field.HEIGHT), ProfileBounds.invalidFields(0, 999, 80.0))
        assertTrue(ProfileBounds.invalidFields(10, 50, 20.0).isEmpty())
        assertTrue(ProfileBounds.invalidFields(110, 272, 400.0).isEmpty())
        assertEquals(78.5, ProfileBounds.parseWeight("78,5"))

        for (w in listOf(-1.0, 0.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val plan = OfflineNutritionPlanner().buildPlan(profile("MALE", 30, 180, w, "MAINTAIN", "SEDENTARY", 3), 0, "en")
            assertTrue(checkNotNull(plan.dailyTarget).kcal > 0, "weight $w")
        }
    }
}

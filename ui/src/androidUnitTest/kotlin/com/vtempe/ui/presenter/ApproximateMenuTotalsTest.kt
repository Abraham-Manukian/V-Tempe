package com.vtempe.ui.presenter

import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** VT-AUD-036: an approximate menu's dishes state no numbers; screens show its daily target, not zeros. */
class ApproximateMenuTotalsTest {
    private val target = Macros(proteinGrams = 120, fatGrams = 60, carbsGrams = 200, kcal = 1820)
    private val dish = Meal("Oats", listOf("oats", "milk"), 0, Macros(0, 0, 0, 0))
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val approximate = NutritionPlan(0, days.associateWith { listOf(dish, dish) }, emptyList(), dailyTarget = target)

    @Test
    fun nutritionTotalsAreTheDailyTarget() {
        assertEquals(MacroTotals(protein = 120, fat = 60, carbs = 200, kcal = 1820), computeDayMacros(approximate, "Mon"))
        assertEquals(1820 * 7, computeWeekMacros(approximate).kcal)
    }

    @Test
    fun aRegularPlanStillTotalsItsMeals() {
        val regular = NutritionPlan(0, mapOf("Mon" to listOf(Meal("Bowl", listOf("rice"), 500, Macros(30, 15, 60, 500)))), emptyList())
        assertEquals(500, computeDayMacros(regular, "Mon").kcal)
        assertEquals(MacroTotals.EMPTY, computeDayMacros(regular, "Tue"))
    }

    @Test
    fun progressChartsTheTargetForEveryDay() {
        val state = buildProgressState(emptyList(), approximate, LocalDate(2026, 10, 7), 2026 to 10, null, epochDate = null, progress = emptyMap())
        assertEquals(List(7) { 1820 }, state.caloriesSeries)
    }
}

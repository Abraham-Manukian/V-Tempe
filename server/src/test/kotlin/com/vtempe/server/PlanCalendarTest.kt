package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.buildTrainingSectionPrompt
import com.vtempe.server.features.ai.data.service.fallbackTraining
import com.vtempe.server.features.ai.data.service.normalizeTrainingPlan
import com.vtempe.server.features.ai.data.service.resolveWeekStart
import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale

/** VT-AUD-012 (plan dated in the client's week on the chosen weekdays) and VT-AUD-013 (one session per chosen day). */
class PlanCalendarTest {

    private val catalog = BuiltInExerciseCatalog()
    private val resolver = DefaultTrainingPlanResolver(catalog)
    private val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    private fun profile(selected: List<String>, split: String = "AUTO") = AiProfile(
        age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 3,
        equipment = listOf("barbell", "dumbbells", "bench"), trainingMode = "GYM",
        weeklySchedule = selected.associateWith { true }, splitPreference = split,
    )

    @Test
    fun `the client's week start is used, snapped to Monday, and only when plausible`() {
        val today = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 12, 28), resolveWeekStart("2026-12-30", today))
        assertEquals(LocalDate.of(2026, 10, 5), resolveWeekStart(null, today))
        assertEquals(LocalDate.of(2026, 10, 5), resolveWeekStart("not a date", today))
        assertEquals(LocalDate.of(2026, 10, 5), resolveWeekStart("2031-01-01", today), "years away is not a plan week")
    }

    @Test
    fun `one model workout becomes the full week on the chosen weekdays, across a year boundary`() {
        val weekStart = LocalDate.of(2026, 12, 28)
        val modelAnswer = AiTrainingResponse(52, listOf(AiWorkout(id = "x", date = "2030-01-01", sets = listOf(AiSet("squat", 5, 100.0, 8.0)))))
        val plan = normalizeTrainingPlan(modelAnswer, profile(listOf("Mon", "Wed", "Fri")), resolver, enforcedWeekIndex = 52, weekStart = weekStart)
        assertEquals(listOf("2026-12-28", "2026-12-30", "2027-01-01"), plan.workouts.map { it.date })
        assertTrue(plan.workouts.all { it.sets.size > 1 })
    }

    @Test
    fun `fallback and prompt use the same chosen dates`() {
        val p = profile(listOf("Tue", "Sat"))
        val fallback = fallbackTraining(AiTrainingRequest(p, 3, "en", weekStart = "2026-10-26"))
        assertEquals(listOf("2026-10-27", "2026-10-31"), fallback.workouts.map { it.date })

        val prompt = buildTrainingSectionPrompt(
            Json, catalog, resolver, p, 3, Locale.ENGLISH, "en", "metric", "kilograms", weekStart = LocalDate.of(2026, 10, 26)
        )
        assertTrue(prompt.contains("2026-10-27") && prompt.contains("2026-10-31"))
    }

    @Test
    fun `sessions match the chosen days for every split preference`() {
        for (n in 1..7) for (split in listOf("AUTO", "FULL_BODY", "UPPER_LOWER", "PPL", "BRO_SPLIT")) {
            val selected = days.take(n)
            val sessions = TrainingSplitPlanner.build(
                trainingDays = selected, focusRaw = "GENERAL", goalRaw = "MAINTAIN", splitPreferenceRaw = split,
                experienceLevel = 3, age = 30, sexRaw = "MALE", lifestyleRaw = "SEDENTARY", injuries = emptyList(),
                sessionDurationMins = 60, weekIndex = 0,
            )
            assertEquals(n.coerceAtMost(6), sessions.size, "$n days, $split")
            assertTrue(sessions.all { it.slots.isNotEmpty() })
        }
    }
}

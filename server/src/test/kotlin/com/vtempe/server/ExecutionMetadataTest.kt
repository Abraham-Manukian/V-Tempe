package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.CoachEditApplicator
import com.vtempe.server.features.ai.data.service.normalizeTrainingPlan
import com.vtempe.server.features.ai.data.service.withExecutionMetadata
import com.vtempe.server.shared.dto.chat.CoachEditOp
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** VT-AUD-022 / 024: every issued set carries its unit, load type and prescribed rest. */
class ExecutionMetadataTest {

    private val catalog = BuiltInExerciseCatalog()

    @Test
    fun `generated sets carry catalog unit, load type and the skeleton's rest`() {
        val profile = AiProfile(age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "GAIN_MUSCLE", experienceLevel = 3,
            equipment = listOf("barbell", "dumbbells", "bench"), trainingMode = "GYM", weeklySchedule = mapOf("Mon" to true, "Thu" to true))
        val plan = AiTrainingResponse(0, List(2) { AiWorkout(id = "w$it", date = "2026-10-05", sets = listOf(AiSet("x", 8))) })
        val sets = normalizeTrainingPlan(plan, profile).workouts.flatMap { it.sets }
        assertTrue(sets.isNotEmpty())
        sets.forEach { set ->
            val item = catalog.findByIdOrAlias(set.exerciseId)!!
            assertEquals(item.durationUnit.name, set.unit, set.exerciseId)
            assertEquals(item.isBodyweightOnly, set.bodyweight, set.exerciseId)
            assertTrue(set.restSeconds != null && set.restSeconds!! in 15..600, "${set.exerciseId}: ${set.restSeconds}")
        }
        assertTrue(sets.map { it.restSeconds }.distinct().size > 1, "rest differs by loading tier: ${sets.map { it.restSeconds }}")
    }

    @Test
    fun `timed exercises are marked as seconds and impossible rest is dropped`() {
        val wallSit = AiSet("wall_sit", 30, restSeconds = 99_999).withExecutionMetadata(catalog)
        assertEquals("SECONDS", wallSit.unit)
        assertNull(wallSit.restSeconds)
    }

    @Test
    fun `sets added by the coach in chat carry the same metadata`() {
        val applicator = CoachEditApplicator(catalog, DefaultTrainingPlanResolver(catalog))
        val current = AiTrainingResponse(0, listOf(AiWorkout(id = "w1", date = "2026-10-05", sets = listOf(AiSet("bench", 8, 60.0, 7.0)))))
        val result = applicator.apply(listOf(CoachEditOp(op = "add_exercise", workoutId = "w1", newExerciseId = "plank")), current, null,
            AiProfile(age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = 2))
        val plank = result.trainingPlan!!.workouts.single().sets.single { it.exerciseId == "plank" }
        assertEquals("SECONDS", plank.unit)
        assertEquals(true, plank.bodyweight)
    }
}

package com.vtempe.shared.data.network.dto

import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** VT-AUD-017: the number of sets survives domain -> DTO -> JSON -> domain. */
class TrainingPlanDtoRoundTripTest {

    @Test
    fun setCountSurvivesEveryMapping() {
        val counts = listOf(1, 2, 3, 5, 10)
        val plan = TrainingPlan(0, listOf(Workout("w_0_0", date = LocalDate(2026, 10, 5),
            sets = counts.mapIndexed { i, n -> WorkoutSet("ex$i", 8, 40.0, 7.0, sets = n) })))
        val json = Json { encodeDefaults = true }
        val restored = json.decodeFromString(TrainingPlanDto.serializer(), json.encodeToString(TrainingPlanDto.serializer(), TrainingPlanDto.fromDomain(plan))).toDomain()
        assertEquals(counts, restored.workouts.single().sets.map { it.sets })
    }

    @Test
    fun legacyPayloadWithoutSetsKeepsTheDocumentedDefault() {
        val legacy = """{"weekIndex":0,"workouts":[{"id":"w","date":"2026-10-05","sets":[{"exerciseId":"squat","reps":8}]}]}"""
        val plan = Json { ignoreUnknownKeys = true }.decodeFromString(TrainingPlanDto.serializer(), legacy).toDomain()
        assertEquals(3, plan.workouts.single().sets.single().sets)
    }
}

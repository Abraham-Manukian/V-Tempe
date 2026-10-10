package com.vtempe.ui.presenter

import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.SetResult
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** VT-AUD-068: Progress counts what was done, and the weekly bars are this week only. */
class ProgressStatsTest {
    private val today = LocalDate(2026, 10, 7) // a Wednesday
    private val mondays = listOf(LocalDate(2026, 9, 28), LocalDate(2026, 10, 5), LocalDate(2026, 10, 12))
    private val plan = mondays.mapIndexed { i, day ->
        Workout("w$i", date = day, sets = listOf(WorkoutSet("squat", reps = 10, weightKg = 10.0 * (i + 1), sets = 3)))
    }

    private fun done(id: String, kg: Double, sets: Int = 3) = id to WorkoutProgress(
        workoutId = id,
        performedSets = listOf(
            PerformedSet(0, completed = true, completedSetsCount = sets, exerciseId = "squat", setResults = List(sets) { SetResult(10, kg) })
        ),
    )

    private fun state(progress: Map<String, WorkoutProgress>) =
        buildProgressState(plan, null, today, 2026 to 10, null, epochDate = null, progress = progress)

    @Test
    fun plannedWorkoutsAloneAreNoAchievement() {
        val s = state(emptyMap())
        assertEquals(0, s.totalWorkouts)
        assertEquals(0, s.totalSets)
        assertEquals(0, s.totalVolume)
        assertEquals(List(7) { 0 }, s.weeklyVolumes)
        assertEquals(mondays.toSet(), s.workoutDates, "the calendar still shows the plan")
    }

    @Test
    fun loggedSetsCountAndOtherWeeksStayOutOfTheBars() {
        val s = state(mapOf(done("w0", 10.0), done("w1", 20.0, sets = 2)))
        assertEquals(2, s.totalWorkouts)
        assertEquals(5, s.totalSets)
        assertEquals(300 + 400, s.totalVolume)
        assertEquals(400, s.weeklyVolumes[0], "Monday is this week's Monday only")
        assertEquals(400, s.weeklyVolumes.sum())
    }
}

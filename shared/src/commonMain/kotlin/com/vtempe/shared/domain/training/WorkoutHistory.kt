package com.vtempe.shared.domain.training

import com.vtempe.shared.domain.model.ExercisePerformance
import com.vtempe.shared.domain.model.SetResult
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSummary

/**
 * Turns one workout's progress into the history entry shown on the progress screen and sent to
 * the coach for progression/deload (VT-AUD-019, 020).
 *
 * - Volume, RPE and per-exercise results come from every completed set, including partly
 *   finished exercises and results of exercises a later plan version replaced.
 * - Completion compares the sets done for the CURRENT plan with its planned sets, so a
 *   replaced exercise's old sets never count as progress on the new one.
 */
object WorkoutHistory {

    /** Sets actually done in this workout, extra sets included. */
    fun completedSets(workout: Workout, progress: WorkoutProgress): Int =
        resultsByExercise(workout, progress).sumOf { it.second.size } + progress.extraSets.size

    fun summarize(workout: Workout, progress: WorkoutProgress): WorkoutSummary? {
        val current = WorkoutProgressEditor.performedFor(workout, progress)
        val all = resultsByExercise(workout, progress)
        val doneSets = all.sumOf { it.second.size }
        if (doneSets == 0 && progress.extraSets.isEmpty() && progress.notes.isBlank()) return null

        val plannedSets = workout.sets.sumOf { it.sets.coerceAtLeast(1) }
        val doneForPlan = current.entries.sumOf { (index, record) ->
            val planned = workout.sets[index]
            WorkoutProgressEditor.resultsOf(record, planned).size.coerceAtMost(planned.sets.coerceAtLeast(1))
        }
        val completedItems = current.count { (index, record) ->
            val planned = workout.sets[index]
            WorkoutProgressEditor.resultsOf(record, planned).size >= planned.sets.coerceAtLeast(1)
        }

        val results = all.flatMap { it.second }
        val volume = results.sumOf { it.volumeKg() } +
            progress.extraSets.sumOf { it.reps * (it.weightKg?.takeIf { w -> w.isFinite() && w > 0 } ?: 0.0) }
        val rpes = results.mapNotNull { it.rpe } + progress.extraSets.mapNotNull { it.rpe }

        return WorkoutSummary(
            workoutId = workout.id,
            date = workout.date.toString(),
            completionRate = if (plannedSets == 0) 0.0 else doneForPlan.toDouble() / plannedSets,
            completedItems = completedItems,
            plannedItems = workout.sets.size,
            totalVolumeKg = volume,
            averageRpe = rpes.takeIf { it.isNotEmpty() }?.average(),
            notes = progress.notes.trim(),
            exercises = all.groupBy({ it.first }, { it.second })
                .mapNotNull { (exerciseId, sets) -> topSet(sets.flatten())?.let { ExercisePerformance(exerciseId, it.weightKg, it.reps) } },
        )
    }

    /** Every record with its exercise and sets: current-plan ones use their plan slot for legacy
     *  defaults; history-only ones (replaced exercises) stand on their own. */
    private fun resultsByExercise(workout: Workout, progress: WorkoutProgress): List<Pair<String, List<SetResult>>> {
        val current = WorkoutProgressEditor.performedFor(workout, progress)
        return progress.performedSets.mapNotNull { record ->
            val slot = current.entries.firstOrNull { it.value === record }?.key
            val planned = slot?.let(workout.sets::getOrNull)
            val exerciseId = record.exerciseId ?: planned?.exerciseId ?: return@mapNotNull null
            exerciseId to WorkoutProgressEditor.resultsOf(record, planned)
        }
    }

    /** The heaviest working set (then most reps) — the number progression should build on. */
    private fun topSet(sets: List<SetResult>): SetResult? =
        sets.maxWithOrNull(compareBy<SetResult>({ it.weightKg ?: 0.0 }, { it.reps }))

    private fun SetResult.volumeKg(): Double = reps * (weightKg?.takeIf { it.isFinite() && it > 0 } ?: 0.0)
}

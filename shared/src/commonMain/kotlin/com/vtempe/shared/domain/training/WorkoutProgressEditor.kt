package com.vtempe.shared.domain.training

import com.vtempe.shared.domain.exercise.ExerciseLibrary
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.SetResult
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet

/**
 * The only place that reads or changes per-exercise progress (VT-AUD-019, 020, 023).
 *
 * - A record belongs to the exercise it was done for ([PerformedSet.exerciseId]), not just to a
 *   position: when a plan is regenerated or edited, results follow their exercise or stay with
 *   the history of the old plan — they are never shown as done for a different exercise.
 * - Every completed set is stored ([PerformedSet.setResults]); the count can never leave
 *   0..planned sets, and undo removes exactly the last completed set.
 */
object WorkoutProgressEditor {

    /** Records that describe this workout's CURRENT planned exercises, keyed by plan index.
     *  Records of exercises no longer in the plan are left out (they remain history). */
    fun performedFor(workout: Workout, progress: WorkoutProgress): Map<Int, PerformedSet> {
        val records = progress.performedSets
        val result = mutableMapOf<Int, PerformedSet>()
        val placed = BooleanArray(records.size)
        // 1. Records still at the index of their own exercise.
        records.forEachIndexed { r, record ->
            val atIndex = workout.sets.getOrNull(record.setIndex)?.exerciseId
            if (record.exerciseId != null && record.setIndex !in result && sameExercise(atIndex, record.exerciseId)) {
                result[record.setIndex] = record
                placed[r] = true
            }
        }
        // 2. Exercises the new plan moved to another position.
        records.forEachIndexed { r, record ->
            if (placed[r] || record.exerciseId == null) return@forEachIndexed
            val index = workout.sets.indices.firstOrNull { i ->
                i !in result && sameExercise(workout.sets[i].exerciseId, record.exerciseId)
            } ?: return@forEachIndexed
            result[index] = record
            placed[r] = true
        }
        // 3. Legacy records without an exercise id: their position is all that is known.
        records.forEach { record ->
            if (record.exerciseId == null && record.setIndex in workout.sets.indices && record.setIndex !in result) {
                result[record.setIndex] = record
            }
        }
        return result
    }

    /** Completed sets of [record]. Legacy records only kept a count and the last values;
     *  they are expanded with those values (or the plan's) so totals stay meaningful. */
    fun resultsOf(record: PerformedSet, planned: WorkoutSet?): List<SetResult> {
        if (record.setResults.isNotEmpty()) return record.setResults
        val count = when {
            record.completedSetsCount > 0 -> record.completedSetsCount
            record.completed -> planned?.sets?.coerceAtLeast(1) ?: 1
            else -> 0
        }
        val reps = record.actualReps ?: planned?.reps ?: return emptyList()
        return List(count) { SetResult(reps, record.actualWeightKg ?: planned?.weightKg, record.actualRpe ?: planned?.rpe) }
    }

    /** Adds one completed set to the exercise at [exerciseIndex]; ignored once all planned sets are done. */
    fun markSetDone(progress: WorkoutProgress, workout: Workout, exerciseIndex: Int, result: SetResult): WorkoutProgress {
        val planned = workout.sets.getOrNull(exerciseIndex) ?: return progress
        val existing = performedFor(workout, progress)[exerciseIndex]
        val done = existing?.let { resultsOf(it, planned) }.orEmpty()
        if (done.size >= planned.sets.coerceAtLeast(1)) return progress
        return progress.replace(existing, record(exerciseIndex, planned, done + result))
    }

    /** Removes the last completed set of the exercise at [exerciseIndex] (no-op when none). */
    fun undoLastSet(progress: WorkoutProgress, workout: Workout, exerciseIndex: Int): WorkoutProgress {
        val planned = workout.sets.getOrNull(exerciseIndex) ?: return progress
        val existing = performedFor(workout, progress)[exerciseIndex] ?: return progress
        val remaining = resultsOf(existing, planned).dropLast(1)
        return progress.replace(existing, remaining.takeIf { it.isNotEmpty() }?.let { record(exerciseIndex, planned, it) })
    }

    /** Stamps legacy records (no exerciseId) with the exercise of the plan they were made for.
     *  Call with the plan that is about to be replaced, before it is overwritten. */
    fun bindLegacyRecords(progress: WorkoutProgress, workout: Workout): WorkoutProgress {
        if (progress.performedSets.none { it.exerciseId == null }) return progress
        return progress.copy(performedSets = progress.performedSets.map { record ->
            val planned = workout.sets.getOrNull(record.setIndex)
            if (record.exerciseId != null || planned == null) record
            else record.copy(exerciseId = planned.exerciseId, setResults = resultsOf(record, planned))
        })
    }

    fun sameExercise(a: String?, b: String?): Boolean =
        a != null && b != null && canonical(a) == canonical(b)

    private fun canonical(id: String): String =
        ExerciseLibrary.findByIdOrAlias(id)?.id ?: id.trim().lowercase().replace('-', '_').replace(' ', '_')

    private fun record(index: Int, planned: WorkoutSet, results: List<SetResult>): PerformedSet {
        val last = results.last()
        return PerformedSet(
            setIndex = index,
            completed = results.size >= planned.sets.coerceAtLeast(1),
            completedSetsCount = results.size,
            actualReps = last.reps,
            actualWeightKg = last.weightKg,
            actualRpe = last.rpe,
            exerciseId = planned.exerciseId,
            setResults = results,
        )
    }

    /** Replaces [old] (by identity) with [new]; removes it when [new] is null, appends when [old] is null. */
    private fun WorkoutProgress.replace(old: PerformedSet?, new: PerformedSet?): WorkoutProgress {
        val sets = performedSets.filterNot { it === old }
        return copy(performedSets = if (new == null) sets else sets + new)
    }
}

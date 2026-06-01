package com.vtempe.shared.data.repo

import com.vtempe.shared.db.SelectWorkoutsWithSets
import com.vtempe.shared.db.SelectWorkoutsWithSetsByWeek
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.jvm.JvmName

// SQLDelight generates a separate data class per query even when the projected columns are
// identical. Both overloads below share the same logic via the private helper.

/**
 * Maps raw [SelectWorkoutsWithSets] rows (all-weeks query) to domain [Workout] objects.
 */
@JvmName("toWorkoutDomainListAllWeeks")
internal fun List<SelectWorkoutsWithSets>.toWorkoutDomainList(): List<Workout> =
    mapRows(
        ids        = map { it.id },
        groupById  = { groupBy { it.id } },
        rowDate    = { it.date },
        rowExerciseId = { it.exerciseId },
        rowReps    = { it.reps },
        rowWeight  = { it.weightKg },
        rowRpe     = { it.rpe },
    )

/**
 * Maps raw [SelectWorkoutsWithSetsByWeek] rows (single-week query) to domain [Workout] objects.
 * Same columns as the all-weeks query — different SQLDelight-generated type.
 */
@JvmName("toWorkoutDomainListByWeek")
internal fun List<SelectWorkoutsWithSetsByWeek>.toWorkoutDomainList(): List<Workout> =
    mapRows(
        ids        = map { it.id },
        groupById  = { groupBy { it.id } },
        rowDate    = { it.date },
        rowExerciseId = { it.exerciseId },
        rowReps    = { it.reps },
        rowWeight  = { it.weightKg },
        rowRpe     = { it.rpe },
    )

// ---------------------------------------------------------------------------
// Shared implementation
// ---------------------------------------------------------------------------

private fun <T> List<T>.mapRows(
    ids: List<String>,
    groupById: List<T>.() -> Map<String, List<T>>,
    rowDate: (T) -> String?,
    rowExerciseId: (T) -> String?,
    rowReps: (T) -> Long?,
    rowWeight: (T) -> Double?,
    rowRpe: (T) -> Double?,
): List<Workout> {
    val fallbackDate = Clock.System.now()
        .toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    return groupById().map { (id, rows) ->
        Workout(
            id   = id,
            date = LocalDate.parse(rowDate(rows.first()) ?: fallbackDate),
            sets = rows
                .filter { rowExerciseId(it) != null }
                .map { row ->
                    WorkoutSet(
                        exerciseId = rowExerciseId(row)!!,
                        reps       = rowReps(row)!!.toInt(),
                        weightKg   = rowWeight(row),
                        rpe        = rowRpe(row),
                    )
                }
        )
    }
}

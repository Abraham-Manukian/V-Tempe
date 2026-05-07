package com.vtempe.shared.data.repo

import com.vtempe.shared.db.SelectWorkoutsWithSets
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Maps raw [SelectWorkoutsWithSets] rows to domain [Workout] objects.
 * Shared by [TrainingRepositoryDb] and [WorkoutProgressStore] to avoid duplicated mapping logic.
 */
internal fun List<SelectWorkoutsWithSets>.toWorkoutDomainList(): List<Workout> =
    groupBy { it.id }
        .map { (id, rows) ->
            val dateStr = rows.firstOrNull()?.date
                ?: Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
            Workout(
                id = id,
                date = LocalDate.parse(dateStr),
                sets = rows
                    .filter { it.exerciseId != null }
                    .map { row ->
                        WorkoutSet(
                            exerciseId = row.exerciseId!!,
                            reps = row.reps!!.toInt(),
                            weightKg = row.weightKg,
                            rpe = row.rpe
                        )
                    }
            )
        }

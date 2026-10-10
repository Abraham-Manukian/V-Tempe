package com.vtempe.shared.domain.exercise

import com.vtempe.shared.domain.model.WorkoutSet

/**
 * How a planned set is performed and recorded (VT-AUD-022). The unit the server prescribed wins
 * — the app's exercise library does not describe every exercise the server can assign, and
 * guessing "weight and reps" turned a 30-second wall sit into 30 repetitions with a weight
 * field. Plans stored before the unit was sent fall back to the library, then to weight/reps.
 */
fun WorkoutSet.executionKind(): ExerciseCalibrationKind = when (unit?.uppercase()) {
    "SECONDS" -> ExerciseCalibrationKind.DURATION_SECONDS
    "MINUTES" -> ExerciseCalibrationKind.DURATION_MINUTES
    "REPS" -> if (bodyweight == true) ExerciseCalibrationKind.BODYWEIGHT_REPS else ExerciseCalibrationKind.WEIGHT_AND_REPS
    else -> ExerciseLibrary.findByIdOrAlias(exerciseId)?.calibrationKind ?: ExerciseCalibrationKind.WEIGHT_AND_REPS
}

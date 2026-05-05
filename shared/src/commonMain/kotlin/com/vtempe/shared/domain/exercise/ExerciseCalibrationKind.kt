package com.vtempe.shared.domain.exercise

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseCalibrationKind {
    WEIGHT_AND_REPS,
    BODYWEIGHT_REPS,
    DURATION_SECONDS,
    DURATION_MINUTES
}

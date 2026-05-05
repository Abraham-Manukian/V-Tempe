package com.vtempe.shared.domain.exercise

import kotlinx.serialization.Serializable

@Serializable
data class ExerciseCalibrationRecord(
    val exerciseId: String,
    val kind: ExerciseCalibrationKind,
    val baselineWeightKg: Double? = null,
    val baselineReps: Int? = null,
    val baselineSeconds: Int? = null,
    val perceivedEffort: Int? = null,
    val updatedAtEpochMs: Long = 0L
)

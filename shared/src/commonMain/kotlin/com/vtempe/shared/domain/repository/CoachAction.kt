package com.vtempe.shared.domain.repository

data class CoachAction(
    val type: CoachActionType,
    val trainingMode: String? = null,
    val weekIndex: Int? = null,
    val notes: String? = null,
    val workoutId: String? = null,
    val targetExerciseId: String? = null,
    val replacementExerciseId: String? = null
)

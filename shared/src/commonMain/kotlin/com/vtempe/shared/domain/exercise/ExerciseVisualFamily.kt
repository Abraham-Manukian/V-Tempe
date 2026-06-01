package com.vtempe.shared.domain.exercise

import kotlinx.serialization.Serializable

@Serializable
enum class ExerciseVisualFamily {
    LOWER_BODY,
    PUSH,
    PULL,
    OVERHEAD,
    ARMS,
    CORE,
    CARDIO,
    GENERIC
}

package com.vtempe.server.shared.dto.chat

import kotlinx.serialization.Serializable

@Serializable
data class AiChatAction(
    val type: String,
    val trainingMode: String? = null,
    val weekIndex: Int? = null,
    val notes: String? = null,
    val workoutId: String? = null,
    val targetExerciseId: String? = null,
    val replacementExerciseId: String? = null
)

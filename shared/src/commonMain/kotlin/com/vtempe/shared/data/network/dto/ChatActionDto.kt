package com.vtempe.shared.data.network.dto

import com.vtempe.shared.domain.repository.CoachAction
import com.vtempe.shared.domain.repository.CoachActionType
import kotlinx.serialization.Serializable

@Serializable
data class ChatActionDto(
    val type: String,
    val trainingMode: String? = null,
    val weekIndex: Int? = null,
    val notes: String? = null,
    val workoutId: String? = null,
    val targetExerciseId: String? = null,
    val replacementExerciseId: String? = null
) {
    fun toDomain(): CoachAction? {
        val actionType = CoachActionType.fromWire(type) ?: return null
        return CoachAction(
            type = actionType,
            trainingMode = trainingMode?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            weekIndex = weekIndex,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            workoutId = workoutId?.trim()?.takeIf { it.isNotEmpty() },
            targetExerciseId = targetExerciseId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
            replacementExerciseId = replacementExerciseId?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        )
    }

    companion object {
        fun fromDomain(action: CoachAction): ChatActionDto = ChatActionDto(
            type = action.type.wireValue,
            trainingMode = action.trainingMode,
            weekIndex = action.weekIndex,
            notes = action.notes,
            workoutId = action.workoutId,
            targetExerciseId = action.targetExerciseId,
            replacementExerciseId = action.replacementExerciseId
        )
    }
}

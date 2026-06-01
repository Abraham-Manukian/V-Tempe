package com.vtempe.server.features.ai.domain.port

import com.vtempe.server.features.ai.domain.model.TrainingMode

interface TrainingPlanResolver {
    fun resolveMode(trainingModeRaw: String?, equipment: List<String>): TrainingMode
    fun normalizeEquipment(equipment: List<String>): Set<String>
    fun resolveExerciseId(
        rawToken: String,
        trainingModeRaw: String?,
        equipment: List<String>,
        usedExerciseIds: Set<String> = emptySet(),
        rotationSeed: Int = 0
    ): String?
}

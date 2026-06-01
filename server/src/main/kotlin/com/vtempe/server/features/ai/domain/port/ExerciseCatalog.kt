package com.vtempe.server.features.ai.domain.port

import com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.TrainingMode

interface ExerciseCatalog {
    fun all(): List<ExerciseCatalogItem>
    fun supportedExerciseIds(): Set<String>
    fun findByIdOrAlias(rawToken: String): ExerciseCatalogItem?
    fun availablePatterns(mode: TrainingMode, equipment: Set<String>): List<MovementPattern>
    fun candidatesFor(pattern: MovementPattern, mode: TrainingMode, equipment: Set<String>): List<ExerciseCatalogItem>
}

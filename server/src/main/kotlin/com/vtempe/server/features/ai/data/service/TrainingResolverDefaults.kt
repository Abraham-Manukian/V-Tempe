package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver

internal val builtInExerciseCatalog: ExerciseCatalog = BuiltInExerciseCatalog()
internal val builtInTrainingPlanResolver: TrainingPlanResolver = DefaultTrainingPlanResolver(builtInExerciseCatalog)

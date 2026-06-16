package com.vtempe.server.features.ai.data.service.split

/** Session-level training parameters derived from TrainingFocus + experience + session duration. */
internal data class SplitParams(
    val setsCompound: Int,
    val setsIsolation: Int,
    val compoundRepMin: Int,
    val compoundRepMax: Int,
    val isolationRepMin: Int,
    val isolationRepMax: Int,
    val rpeCompound: Float,
    val rpeIsolation: Float,
    val restCompoundSeconds: Int,
    val restIsolationSeconds: Int,
    val exercisesPerSession: Int
)

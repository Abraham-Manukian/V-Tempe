package com.vtempe.server.shared.dto.training

import kotlinx.serialization.Serializable

@Serializable
data class AiSet(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val sets: Int = 3,
    /** Recommended rest after each set, from the plan's skeleton (VT-AUD-024); null = not prescribed. */
    val restSeconds: Int? = null,
    /** What [reps] counts: "REPS", "SECONDS" or "MINUTES" (VT-AUD-022). Filled by the server. */
    val unit: String? = null,
    /** True when the exercise takes no external load (weight stays empty). Filled by the server. */
    val bodyweight: Boolean? = null,
)

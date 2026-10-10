package com.vtempe.server.shared.dto.training

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiTrainingRequest(
    val profile: AiProfile,
    val weekIndex: Int,
    val locale: String? = null,
    /** ISO date of the first day of the planned calendar week (the client's epoch + 7 x weekIndex).
     *  Absent from older clients — the current week is used then (VT-AUD-012). */
    val weekStart: String? = null,
)

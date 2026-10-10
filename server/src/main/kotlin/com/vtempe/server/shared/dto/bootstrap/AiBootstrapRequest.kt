package com.vtempe.server.shared.dto.bootstrap

import com.vtempe.server.shared.dto.profile.AiProfile
import kotlinx.serialization.Serializable

@Serializable
data class AiBootstrapRequest(
    val profile: AiProfile,
    val weekIndex: Int = 0,
    val locale: String? = null,
    /** ISO date of the first day of the planned calendar week (the client's epoch + 7 x weekIndex).
     *  Absent from older clients — the current week is used then (VT-AUD-012). */
    val weekStart: String? = null,
)

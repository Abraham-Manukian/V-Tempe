package com.vtempe.server.features.ai.data.service

import com.vtempe.server.config.Env
import java.util.Locale

internal object AiQualityErrorPolicy {
    private val configuredMode = Env["AI_QUALITY_MODE"]
        ?.trim()
        ?.lowercase(Locale.US)
        ?.ifBlank { null }
        ?: "relaxed"
    private val configuredModel = Env["OPENROUTER_MODEL"]?.trim().orEmpty()
    private val relaxedModeEnabled = when (configuredMode) {
        "strict" -> false
        "free" -> isFreeTierModel(configuredModel)
        else -> true
    }

    fun criticalErrors(errors: List<String>): List<String> {
        if (!relaxedModeEnabled) return errors.distinct()
        return errors.filterNot(::isNonCriticalError).distinct()
    }

    fun warningErrors(errors: List<String>): List<String> {
        if (!relaxedModeEnabled) return emptyList()
        return errors.filter(::isNonCriticalError).distinct()
    }

    fun isRelaxedModeEnabled(): Boolean = relaxedModeEnabled

    private fun isFreeTierModel(model: String): Boolean {
        val normalized = model.trim().lowercase(Locale.US)
        if (normalized.isBlank()) return false
        return normalized.contains(":free") ||
            normalized.contains("/free") ||
            normalized.endsWith("-free")
    }

    private fun isNonCriticalError(error: String): Boolean {
        val normalized = error.lowercase(Locale.US)
        return normalized.contains("meal names repeated too often across week") ||
            normalized.contains("meal frequency") ||
            normalized.contains("contains duplicate meals") ||
            normalized.contains("nutrition language mismatch")
    }
}

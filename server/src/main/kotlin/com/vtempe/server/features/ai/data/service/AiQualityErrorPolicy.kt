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
        // "calorie sum severe deviation" (>20% off target) is deliberately NOT listed here —
        // it must stay critical so it forces a retry instead of shipping a plan that actively
        // works against the user's goal (e.g. a bulking plan landing 30% under target).
        // Only the soft ±10-20% "calorie sum" warning is non-critical.
        return normalized.contains("meal names repeated too often across week") ||
            normalized.contains("meal frequency") ||
            normalized.contains("contains duplicate meals") ||
            normalized.contains("nutrition language mismatch") ||
            (normalized.contains("calorie sum") && !normalized.contains("severe deviation"))
    }
}

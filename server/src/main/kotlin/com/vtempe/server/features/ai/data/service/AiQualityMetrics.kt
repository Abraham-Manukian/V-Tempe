package com.vtempe.server.features.ai.data.service

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import com.vtempe.server.features.ai.data.llm.telemetry.SafeDiagnostics
import org.slf4j.Logger

internal object AiQualityMetrics {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    fun recordValidation(logger: Logger, operation: String, requestId: String?, errors: List<String>) {
        if (errors.isEmpty()) return
        errors.forEach { error ->
            val category = errorCategory(error)
            increment("validation:$operation:$category")
        }
        logger.warn(
            "AI quality gate violations operation={} requestId={} errors={}",
            operation,
            requestId ?: "unknown",
            SafeDiagnostics.validationErrors(errors)
        )
    }

    fun recordFallback(logger: Logger, operation: String, reason: Throwable) {
        increment("fallback:$operation")
        logger.warn(
            "AI fallback metric operation={} reason={}",
            operation,
            reason::class.simpleName // messages can quote model output (VT-AUD-001)
        )
    }

    fun recordDecomposedBundle(logger: Logger, requestId: String?) {
        val count = increment("bundle:decomposed")
        logger.info(
            "AI decomposed bundle generation activated requestId={} count={}",
            requestId ?: "unknown",
            count
        )
    }

    private fun increment(key: String): Int =
        counters.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()

    private fun errorCategory(error: String): String {
        val normalized = error.lowercase()
        return when {
            normalized.contains("allergen") || normalized.contains("restriction") -> "allergen"
            normalized.contains("language") || normalized.contains("russian") || normalized.contains("cyrillic") -> "language"
            normalized.contains("duplicate") -> "duplicates"
            normalized.contains("meals/day") || normalized.contains("meal frequency") -> "meal_count"
            normalized.contains("macro") || normalized.contains("kcal") -> "macros"
            else -> "other"
        }
    }
}

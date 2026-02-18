package com.vtempe.server.features.ai.data.llm.telemetry

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import org.slf4j.Logger

class LlmErrorTracker {
    private val counters = ConcurrentHashMap<String, AtomicInteger>()

    fun success(logger: Logger, operation: String, requestId: String?, attempt: Int, fixes: Set<String>) {
        inc("success:$operation")
        logger.info(
            "LLM {} requestId={} success attempt={} fixes={}",
            operation,
            requestId ?: "unknown",
            attempt,
            if (fixes.isEmpty()) "-" else fixes.joinToString(",")
        )
    }

    fun fail(logger: Logger, operation: String, requestId: String?, attempt: Int, stage: String, message: String, snippet: String?) {
        inc("fail:$operation:$stage")
        logger.warn(
            "LLM {} requestId={} attempt={} stage={} message={} snippet={}",
            operation,
            requestId ?: "unknown",
            attempt,
            stage,
            message,
            snippet ?: "<none>"
        )
    }

    private fun inc(key: String) {
        counters.computeIfAbsent(key) { AtomicInteger(0) }.incrementAndGet()
    }
}

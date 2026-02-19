package com.vtempe.server.features.ai.data.llm.pipeline

import com.vtempe.server.features.ai.data.llm.decode.Decoder
import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.extract.ExtractionResult
import com.vtempe.server.features.ai.data.llm.extract.ResponseExtractor
import com.vtempe.server.features.ai.data.llm.feedback.FeedbackComposer
import com.vtempe.server.features.ai.data.llm.repair.JsonSanitizer
import com.vtempe.server.features.ai.data.llm.telemetry.LlmErrorTracker
import com.vtempe.server.features.ai.data.llm.telemetry.LlmRawStore
import kotlinx.serialization.DeserializationStrategy
import org.slf4j.Logger

class LlmPipeline(
    private val config: PipelineConfig,
    private val extractor: ResponseExtractor,
    private val sanitizer: JsonSanitizer,
    private val decoder: Decoder,
    private val feedback: FeedbackComposer,
    private val rawStore: LlmRawStore,
    private val tracker: LlmErrorTracker,
) {
    suspend fun <T> run(
        logger: Logger,
        operation: String,
        requestId: String?,
        basePrompt: String,
        callModel: suspend (prompt: String) -> String,
        strategy: DeserializationStrategy<T>,
        validator: SchemaValidator<T>,
        extractionMode: ExtractionMode = ExtractionMode.FirstJsonObject,
    ): T {
        var fb: String? = null
        val fixes = linkedSetOf<String>()
        var lastRaw = ""

        for (attempt in 1..config.maxAttempts) {
            val prompt = if (fb == null) basePrompt else "$basePrompt\n\n$fb"
            val raw = callModel(prompt).trim()
            lastRaw = raw

            rawStore.write(operation, requestId, attempt, "raw", raw)

            val extracted = extractCandidate(raw, extractionMode)

            val candidate = when (extracted) {
                is ExtractionResult.Success -> extracted.candidate
                is ExtractionResult.Failure -> {
                    val msg = extracted.reason
                    tracker.fail(logger, operation, requestId, attempt, "extract", msg, snippet(raw))
                    fb = feedback.decodeError(msg)
                    continue
                }
            }

            // decode #1
            val decoded1 = decoder.decode(strategy, candidate)
            if (decoded1.isSuccess) {
                val value = decoded1.getOrThrow()
                val errors = validator.validate(value)
                if (errors.isEmpty()) {
                    tracker.success(logger, operation, requestId, attempt, fixes)
                    return value
                }
                tracker.fail(logger, operation, requestId, attempt, "validate", errors.joinToString("; "), snippet(candidate))
                fb = feedback.validationErrors(errors)
                continue
            }

            // sanitize + decode #2
            val repaired = sanitizer.sanitize(candidate)
            fixes += repaired.fixes
            rawStore.write(operation, requestId, attempt, "repaired", repaired.fixed)

            val decoded2 = decoder.decode(strategy, repaired.fixed)
            if (decoded2.isSuccess) {
                val value = decoded2.getOrThrow()
                val errors = validator.validate(value)
                if (errors.isEmpty()) {
                    tracker.success(logger, operation, requestId, attempt, fixes)
                    return value
                }
                tracker.fail(logger, operation, requestId, attempt, "validate", errors.joinToString("; "), snippet(repaired.fixed))
                fb = feedback.validationErrors(errors)
                continue
            }

            val msg = decoded2.exceptionOrNull()?.message ?: decoded1.exceptionOrNull()?.message ?: "decode error"
            tracker.fail(logger, operation, requestId, attempt, "decode", msg, snippet(repaired.fixed))
            fb = feedback.decodeError(msg)
        }

        throw IllegalStateException("LLM $operation failed after ${config.maxAttempts} attempts. lastRaw=${snippet(lastRaw) ?: "<empty>"}")
    }

    private fun extractCandidate(raw: String, extractionMode: ExtractionMode): ExtractionResult =
        when (extractionMode) {
            is ExtractionMode.FirstJsonObject -> extractor.firstJsonObject(raw)
            is ExtractionMode.MarkerAfter -> {
                when (val markerResult = extractor.jsonAfterMarker(raw, extractionMode.marker)) {
                    is ExtractionResult.Success -> markerResult
                    is ExtractionResult.Failure -> {
                        when (val firstObjectResult = extractor.firstJsonObject(raw)) {
                            is ExtractionResult.Success -> firstObjectResult
                            is ExtractionResult.Failure -> ExtractionResult.Failure(
                                "${markerResult.reason}; fallback first-json failed: ${firstObjectResult.reason}"
                            )
                        }
                    }
                }
            }
        }

    private fun snippet(s: String): String? =
        s.replace('\n', ' ').replace('\r', ' ').trim().takeIf { it.isNotEmpty() }?.take(config.rawSnippetLimit)
}

sealed class ExtractionMode {
    data object FirstJsonObject : ExtractionMode()
    data class MarkerAfter(val marker: String) : ExtractionMode()
}

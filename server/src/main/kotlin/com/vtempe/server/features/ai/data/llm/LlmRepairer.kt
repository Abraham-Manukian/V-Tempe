package com.vtempe.server.features.ai.data.llm

import com.vtempe.server.features.ai.data.llm.decode.SchemaValidator
import com.vtempe.server.features.ai.data.llm.pipeline.ExtractionMode
import com.vtempe.server.features.ai.data.llm.pipeline.LlmPipeline
import kotlinx.serialization.DeserializationStrategy
import org.slf4j.Logger

class LlmRepairer(
    private val pipeline: LlmPipeline
) {
    suspend fun <T> generate(
        logger: Logger,
        operation: String,
        requestId: String? = null,
        basePrompt: String,
        callModel: suspend (String) -> String,
        strategy: DeserializationStrategy<T>,
        validator: SchemaValidator<T>,
        extractionMode: ExtractionMode = ExtractionMode.FirstJsonObject
    ): T = pipeline.run(
        logger = logger,
        operation = operation,
        requestId = requestId,
        basePrompt = basePrompt,
        callModel = callModel,
        strategy = strategy,
        validator = validator,
        extractionMode = extractionMode
    )
}

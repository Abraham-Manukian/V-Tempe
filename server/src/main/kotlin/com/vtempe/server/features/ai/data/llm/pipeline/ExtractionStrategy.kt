package com.vtempe.server.features.ai.data.llm.pipeline

fun interface ExtractionStrategy {
    fun extract(raw: String): ExtractionResult
}

sealed class ExtractionResult {
    data class JsonCandidate(val json: String) : ExtractionResult()
    data class Failure(val reason: String) : ExtractionResult()
}

class FirstJsonObjectExtraction(
    private val extractor: ResponseExtractor
) : ExtractionStrategy {
    override fun extract(raw: String): ExtractionResult {
        val json = extractor.extractFirstJsonObject(raw)
            ?: return ExtractionResult.Failure("No JSON object found in model output")
        return ExtractionResult.JsonCandidate(json)
    }
}

class MarkerJsonExtraction(
    private val extractor: ResponseExtractor,
    private val marker: String
) : ExtractionStrategy {
    override fun extract(raw: String): ExtractionResult {
        val json = extractor.extractJsonAfterMarker(raw, marker)
            ?: return ExtractionResult.Failure("Expected marker '$marker' and JSON object after it")
        return ExtractionResult.JsonCandidate(json)
    }
}

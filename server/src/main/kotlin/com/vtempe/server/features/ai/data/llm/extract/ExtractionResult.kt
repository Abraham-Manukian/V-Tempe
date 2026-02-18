package com.vtempe.server.features.ai.data.llm.extract

sealed class ExtractionResult {
    data class Success(val candidate: String) : ExtractionResult()
    data class Failure(val reason: String) : ExtractionResult()
}

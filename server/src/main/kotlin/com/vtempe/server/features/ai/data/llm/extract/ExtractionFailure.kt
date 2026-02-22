package com.vtempe.server.features.ai.data.llm.extract

data class ExtractionFailure(
    val reason: String
) : ExtractionResult

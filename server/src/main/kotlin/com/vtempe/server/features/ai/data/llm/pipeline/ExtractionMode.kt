package com.vtempe.server.features.ai.data.llm.pipeline

sealed class ExtractionMode {
    data object FirstJsonObject : ExtractionMode()
    data class MarkerAfter(val marker: String) : ExtractionMode()
}

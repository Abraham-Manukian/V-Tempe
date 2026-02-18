package com.vtempe.server.features.ai.data.llm.pipeline

data class PipelineConfig(
    val maxAttempts: Int = 3,
    val rawSnippetLimit: Int = 160,
    val enableRawStore: Boolean = true,
)

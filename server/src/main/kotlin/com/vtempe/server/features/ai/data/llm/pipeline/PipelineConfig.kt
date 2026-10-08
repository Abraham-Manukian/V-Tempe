package com.vtempe.server.features.ai.data.llm.pipeline

data class PipelineConfig(
    val maxAttempts: Int = 3,
    /**
     * How many chars of raw model output may appear in server logs and in
     * [LlmPipelineExhaustedException] messages. 0 (default) logs no model output at all —
     * replies can echo user health data. Opt in locally via LLM_LOG_SNIPPET_CHARS.
     */
    val rawSnippetLimit: Int = 0,
    val enableRawStore: Boolean = true,
)

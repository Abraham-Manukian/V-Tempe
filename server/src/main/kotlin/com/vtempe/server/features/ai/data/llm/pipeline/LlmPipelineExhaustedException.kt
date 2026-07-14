package com.vtempe.server.features.ai.data.llm.pipeline

/**
 * Thrown by [LlmPipeline] when it exhausts its retry budget without producing JSON that both
 * decodes and passes schema validation. This is an EXPECTED failure mode — the model kept
 * returning malformed or invalid JSON, not a bug in our code — so callers use this type to
 * decide it's safe to fall back to a canned/free-tier response instead of surfacing a 500.
 */
class LlmPipelineExhaustedException(message: String) : RuntimeException(message)

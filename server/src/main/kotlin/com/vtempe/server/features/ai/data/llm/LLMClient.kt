package com.vtempe.server.features.ai.data.llm

import com.vtempe.server.features.ai.data.llm.schema.ResponseSchema

interface LLMClient {
    /**
     * @param schema when non-null and structured output is enabled, the provider is asked to
     *   enforce this JSON Schema via constrained decoding. Implementations that cannot enforce it
     *   (local Ollama, stubs) ignore it. Callers must validate the result either way: a schema
     *   guarantees the SHAPE, never the correctness of the content.
     */
    suspend fun generateJson(prompt: String, schema: ResponseSchema? = null): String
}

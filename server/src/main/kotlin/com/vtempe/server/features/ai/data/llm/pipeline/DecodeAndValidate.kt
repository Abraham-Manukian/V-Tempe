package com.vtempe.server.features.ai.data.llm.pipeline

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

class DecodeAndValidate(
    private val json: Json
) {
    fun <T> decode(
        strategy: DeserializationStrategy<T>,
        jsonText: String
    ): Result<T> = runCatching { json.decodeFromString(strategy, jsonText) }
}

fun interface Validator<T> {
    fun validate(value: T): List<String> // пусто = ок
}

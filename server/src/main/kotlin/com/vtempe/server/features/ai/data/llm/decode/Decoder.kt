package com.vtempe.server.features.ai.data.llm.decode

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.Json

class Decoder(private val json: Json) {
    fun <T> decode(strategy: DeserializationStrategy<T>, text: String): Result<T> =
        runCatching { json.decodeFromString(strategy, text) }
}

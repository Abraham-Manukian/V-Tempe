package com.vtempe.server.shared.util

import kotlinx.serialization.json.Json

object JsonProvider {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        explicitNulls = false
    }
}

package com.vtempe.server.features.ai.data.llm.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * OpenRouter/OpenAI `response_format`.
 *
 * `type = "json_schema"` with [jsonSchema] engages **constrained decoding** on providers that
 * support it: the inference engine cannot emit a token that would break the schema, so an invalid
 * shape becomes impossible rather than merely unlikely. `type = "json_object"` (no schema) is the
 * weaker legacy mode — valid JSON, but any shape.
 */
@Serializable
data class ResponseFormatDto(
    val type: String,
    @SerialName("json_schema") val jsonSchema: JsonSchemaDto? = null
)

/**
 * @param strict when true the provider enforces the schema exactly. Note the strict dialect is a
 *   subset of JSON Schema: every property must be listed in `required` and `additionalProperties`
 *   must be false — optional fields are expressed as nullable unions (`["string", "null"]`)
 *   rather than being omitted from `required`. JsonSchemas.kt builds schemas that way.
 */
@Serializable
data class JsonSchemaDto(
    val name: String,
    // No default on purpose: kotlinx omits properties equal to their default, so `strict = true`
    // as a default would be dropped from the payload and the provider would fall back to
    // non-strict enforcement — silently losing the guarantee we are paying for.
    val strict: Boolean,
    val schema: JsonObject
)

/**
 * OpenRouter provider-routing preferences.
 *
 * `require_parameters = true` makes OpenRouter route only to endpoints that actually support every
 * parameter we send. Without it a request carrying `response_format: json_schema` can land on an
 * endpoint that silently ignores it — we would believe the shape is enforced while it is not,
 * which is worse than not asking for it at all.
 */
@Serializable
data class ProviderPreferencesDto(
    // No default — see the note on JsonSchemaDto.strict. A dropped `require_parameters` would let
    // OpenRouter route to an endpoint that ignores the schema entirely.
    @SerialName("require_parameters") val requireParameters: Boolean
)

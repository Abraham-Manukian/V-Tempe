package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.llm.dto.ChatCompletionRequestDto
import com.vtempe.server.features.ai.data.llm.dto.ChatMessageDto
import com.vtempe.server.features.ai.data.llm.dto.JsonSchemaDto
import com.vtempe.server.features.ai.data.llm.dto.ProviderPreferencesDto
import com.vtempe.server.features.ai.data.llm.dto.ResponseFormatDto
import com.vtempe.server.features.ai.data.llm.schema.JsonSchemas
import com.vtempe.server.features.ai.domain.model.MovementPattern
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonSchemaTest {

    private val catalog = BuiltInExerciseCatalog()

    /**
     * Walks every nested object in a schema and asserts the two rules the providers' *strict*
     * dialect enforces. Getting either wrong makes the provider reject the whole request, which
     * would silently push every generation onto the unconstrained fallback path.
     */
    private fun assertStrictDialect(node: JsonObject, path: String = "$") {
        val type = node["type"]
        val isObject = (type as? JsonPrimitive)?.contentOrNullSafe() == "object"
        if (isObject) {
            val props = node["properties"]?.jsonObject
                ?: error("$path: object without 'properties'")
            val required = node["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
                ?: error("$path: object without 'required'")
            assertEquals(
                props.keys, required,
                "$path: strict mode requires EVERY property to be listed in 'required'"
            )
            assertEquals(
                false, (node["additionalProperties"] as? JsonPrimitive)?.content?.toBoolean(),
                "$path: strict mode requires additionalProperties=false"
            )
        }
        // Recurse into properties / items / anyOf branches.
        node["properties"]?.jsonObject?.forEach { (k, v) -> assertStrictDialect(v.jsonObject, "$path.$k") }
        node["items"]?.let { assertStrictDialect(it.jsonObject, "$path[]") }
        (node["anyOf"] as? JsonArray)?.forEachIndexed { i, v ->
            assertStrictDialect(v.jsonObject, "$path|anyOf[$i]")
        }
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? = if (isString) content else null

    @Test
    fun `training schema obeys the strict dialect`() {
        assertStrictDialect(JsonSchemas.trainingPlan(catalog).schema)
    }

    @Test
    fun `nutrition schema obeys the strict dialect`() {
        assertStrictDialect(JsonSchemas.nutritionPlan().schema)
    }

    @Test
    fun `chat schema obeys the strict dialect`() {
        assertStrictDialect(JsonSchemas.chatResponse(catalog).schema)
    }

    @Test
    fun `bundle schema obeys the strict dialect`() {
        assertStrictDialect(JsonSchemas.bootstrapBundle(catalog).schema)
    }

    @Test
    fun `exercise enum covers every catalog id and every resolver pattern token`() {
        val allowed = JsonSchemas.allowedExerciseIds(catalog).toSet()

        // Concrete ids — what the chat/edit prompt tells the model to copy verbatim.
        catalog.supportedExerciseIds().forEach { id ->
            assertTrue(id in allowed, "catalog id '$id' missing from the schema enum")
        }
        // Pattern tokens — what the generation prompt tells the model to emit instead of ids.
        // Omitting these would make the schema contradict the generation prompt.
        MovementPattern.entries.forEach { pattern ->
            assertTrue(pattern.token in allowed, "pattern token '${pattern.token}' missing from enum")
        }
    }

    @Test
    fun `exercise enum is actually wired into the training schema`() {
        val sets = JsonSchemas.trainingPlan(catalog).schema["properties"]!!.jsonObject["workouts"]!!
            .jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject["sets"]!!
            .jsonObject["items"]!!.jsonObject["properties"]!!.jsonObject
        val enumValues = sets["exerciseId"]!!.jsonObject["enum"]!!.jsonArray
            .map { it.jsonPrimitive.content }
        assertTrue("squat" in enumValues, "concrete id missing from exerciseId enum")
        assertTrue("pattern:knee_dominant" in enumValues, "pattern token missing from exerciseId enum")
    }

    @Test
    fun `nutrition schema declares all seven day keys`() {
        val days = JsonSchemas.nutritionPlan().schema["properties"]!!.jsonObject["mealsByDay"]!!
            .jsonObject["properties"]!!.jsonObject.keys
        assertEquals(setOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"), days)
    }

    @Test
    fun `request serializes response_format and require_parameters`() {
        val schema = JsonSchemas.sleepAdvice()
        val body = ChatCompletionRequestDto(
            model = "test/model",
            messages = listOf(ChatMessageDto(role = "user", content = "hi")),
            responseFormat = ResponseFormatDto(
                type = "json_schema",
                jsonSchema = JsonSchemaDto(name = schema.name, strict = true, schema = schema.schema)
            ),
            provider = ProviderPreferencesDto(requireParameters = true)
        )
        val encoded = Json.encodeToString(ChatCompletionRequestDto.serializer(), body)
        assertTrue("\"response_format\"" in encoded, "response_format missing from request payload")
        assertTrue("\"json_schema\"" in encoded, "json_schema missing from request payload")
        assertTrue("\"strict\":true" in encoded, "strict flag missing from request payload")
        assertTrue("\"require_parameters\":true" in encoded, "require_parameters missing")
    }

    @Test
    fun `omitting the schema leaves both fields out of the payload`() {
        val body = ChatCompletionRequestDto(
            model = "test/model",
            messages = listOf(ChatMessageDto(role = "user", content = "hi"))
        )
        val encoded = Json.encodeToString(ChatCompletionRequestDto.serializer(), body)
        assertFalse("response_format" in encoded, "must not send response_format when unset")
        assertFalse("provider" in encoded, "must not send provider preferences when unset")
    }
}

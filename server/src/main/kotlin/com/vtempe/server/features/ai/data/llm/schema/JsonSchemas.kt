package com.vtempe.server.features.ai.data.llm.schema

import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * A JSON Schema handed to the provider as `response_format: json_schema`.
 *
 * The point is to turn probabilistic properties into hard guarantees: with constrained decoding
 * the engine physically cannot emit a token that breaks the schema, so "the model usually returns
 * valid JSON" becomes "invalid JSON is impossible", and "the model usually copies an exercise id
 * from the list" becomes "an id outside the enum is impossible".
 */
data class ResponseSchema(val name: String, val schema: JsonObject)

/**
 * Builds the response schemas for every AI operation.
 *
 * ## Why the strict dialect looks verbose
 * Providers enforce a *subset* of JSON Schema in strict mode: `additionalProperties` must be
 * false and EVERY property must appear in `required`. An optional field is therefore expressed
 * as a nullable union (`["number", "null"]`) instead of being left out of `required` — hence
 * [nullable]. Getting this wrong makes the provider reject the whole request, which is why
 * StructuredOutputPolicy degrades gracefully instead of failing the generation.
 */
object JsonSchemas {

    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    /**
     * Allowed values for `exerciseId`.
     *
     * Deliberately the UNION of concrete catalog ids and resolver pattern tokens, because both are
     * legitimate depending on the path: the generation prompt tells the model to use slot tokens
     * (`pattern:knee_dominant`) which the resolver expands server-side, while the chat/edit prompt
     * hands it concrete ids (`lat_pulldown`) to copy verbatim. An enum of only one kind would
     * contradict one of the two prompts and break that path.
     */
    fun allowedExerciseIds(catalog: ExerciseCatalog): List<String> =
        (catalog.supportedExerciseIds().sorted() + MovementPattern.entries.map { it.token })
            .distinct()

    // ── Public entry points ──────────────────────────────────────────────

    fun trainingPlan(catalog: ExerciseCatalog): ResponseSchema =
        ResponseSchema("training_plan", trainingPlanObject(allowedExerciseIds(catalog)))

    fun nutritionPlan(): ResponseSchema =
        ResponseSchema("nutrition_plan", nutritionPlanObject())

    fun sleepAdvice(): ResponseSchema =
        ResponseSchema("sleep_advice", adviceObject())

    fun bootstrapBundle(catalog: ExerciseCatalog): ResponseSchema {
        val ids = allowedExerciseIds(catalog)
        return ResponseSchema(
            "coach_bundle",
            obj(
                "trainingPlan" to nullableObject(trainingPlanObject(ids)),
                "nutritionPlan" to nullableObject(nutritionPlanObject()),
                "sleepAdvice" to nullableObject(adviceObject()),
            )
        )
    }

    fun chatResponse(catalog: ExerciseCatalog): ResponseSchema {
        val ids = allowedExerciseIds(catalog)
        return ResponseSchema(
            "coach_chat",
            obj(
                "reply" to type("string"),
                "actions" to array(chatActionObject()),
                "editOps" to array(editOpObject(ids)),
                "trainingPlan" to nullableObject(trainingPlanObject(ids)),
                "nutritionPlan" to nullableObject(nutritionPlanObject()),
                "sleepAdvice" to nullableObject(adviceObject()),
            )
        )
    }

    // ── Building blocks ──────────────────────────────────────────────────

    private fun trainingPlanObject(exerciseIds: List<String>): JsonObject = obj(
        "weekIndex" to type("integer"),
        "workouts" to array(
            obj(
                "id" to type("string"),
                "label" to type("string"),
                "date" to type("string", description = "ISO date, YYYY-MM-DD"),
                "sets" to array(
                    obj(
                        "exerciseId" to enum(exerciseIds),
                        "reps" to type("integer"),
                        "weightKg" to nullable("number"),
                        "rpe" to nullable("number"),
                        "sets" to type("integer"),
                    )
                ),
            )
        ),
    )

    private fun nutritionPlanObject(): JsonObject = obj(
        "weekIndex" to type("integer"),
        // A Map<String, List<AiMeal>> cannot use additionalProperties in the strict dialect, but
        // the day keys are a fixed Mon..Sun set, so they are declared as explicit properties.
        "mealsByDay" to obj(*DAYS.map { it to array(mealObject()) }.toTypedArray()),
        "shoppingList" to array(type("string")),
    )

    private fun mealObject(): JsonObject = obj(
        "name" to type("string"),
        "ingredients" to array(type("string")),
        "recipe" to type("string"),
        "allergenTags" to array(type("string")),
        "kcal" to type("integer"),
        "macros" to obj(
            "proteinGrams" to type("integer"),
            "fatGrams" to type("integer"),
            "carbsGrams" to type("integer"),
            "kcal" to type("integer"),
        ),
    )

    private fun adviceObject(): JsonObject = obj(
        "messages" to array(type("string")),
        "disclaimer" to nullable("string"),
    )

    private fun chatActionObject(): JsonObject = obj(
        "type" to type("string"),
        "trainingMode" to nullable("string"),
        "weekIndex" to nullable("integer"),
        "notes" to nullable("string"),
    )

    private fun editOpObject(exerciseIds: List<String>): JsonObject = obj(
        "op" to type("string"),
        "workoutId" to nullable("string"),
        "exerciseId" to nullableEnum(exerciseIds),
        "newExerciseId" to nullableEnum(exerciseIds),
        "weightKg" to nullable("number"),
        "reps" to nullable("integer"),
        "rpe" to nullable("number"),
        "sets" to nullable("integer"),
        "day" to nullable("string"),
        "mealIndex" to nullable("integer"),
        "mealName" to nullable("string"),
        "ingredientIndex" to nullable("integer"),
        "ingredient" to nullable("string"),
        "name" to nullable("string"),
        "kcal" to nullable("integer"),
        "proteinGrams" to nullable("integer"),
        "fatGrams" to nullable("integer"),
        "carbsGrams" to nullable("integer"),
        "ingredients" to nullableArray(type("string")),
        "recipe" to nullable("string"),
    )

    // ── Primitives ───────────────────────────────────────────────────────

    /** Object with every property required and `additionalProperties: false` (strict dialect). */
    private fun obj(vararg properties: Pair<String, JsonObject>): JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            properties.forEach { (name, schema) -> put(name, schema) }
        }
        putJsonArray("required") { properties.forEach { (name, _) -> add(name) } }
        put("additionalProperties", false)
    }

    private fun type(name: String, description: String? = null): JsonObject = buildJsonObject {
        put("type", name)
        description?.let { put("description", it) }
    }

    /** Optional scalar — strict mode keeps it in `required`, so absence is modelled as null. */
    private fun nullable(name: String): JsonObject = buildJsonObject {
        putJsonArray("type") { add(name); add("null") }
    }

    private fun array(items: JsonObject): JsonObject = buildJsonObject {
        put("type", "array")
        put("items", items)
    }

    private fun nullableArray(items: JsonObject): JsonObject = buildJsonObject {
        putJsonArray("type") { add("array"); add("null") }
        put("items", items)
    }

    private fun enum(values: List<String>): JsonObject = buildJsonObject {
        put("type", "string")
        putJsonArray("enum") { values.forEach { add(it) } }
    }

    private fun nullableEnum(values: List<String>): JsonObject = buildJsonObject {
        putJsonArray("type") { add("string"); add("null") }
        // JSON null must itself be a permitted enum value, otherwise "type: null" and the enum
        // contradict each other and the provider rejects the schema.
        putJsonArray("enum") { values.forEach { add(it) }; add(JsonNull) }
    }

    /** Nested object that may be absent — expressed as an anyOf(object, null). */
    private fun nullableObject(inner: JsonObject): JsonObject = buildJsonObject {
        putJsonArray("anyOf") {
            add(inner)
            add(buildJsonObject { put("type", "null") })
        }
    }
}

package com.vtempe.server.features.ai.data.llm.repair

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class JsonSanitizer {

    fun sanitize(raw: String): RepairResult {
        var s = raw.trim().replace("\r", "")
        val fixes = linkedSetOf<String>()

        fun set(label: String, newValue: String) {
            if (newValue != s) {
                s = newValue
                fixes += label
            }
        }

        // remove markdown fences
        if (s.startsWith("```")) {
            val start = s.indexOf('\n')
            val end = s.lastIndexOf("```")
            if (start > 0 && end > start) {
                set("strip_code_fences", s.substring(start + 1, end).trim())
            }
        }

        // strip to outer braces
        val first = s.indexOf('{')
        val last = s.lastIndexOf('}')
        if (first >= 0 && last > first) set("strip_prefix_suffix", s.substring(first, last + 1))

        // balance braces minimally
        val diff = s.count { it == '{' } - s.count { it == '}' }
        if (diff > 0) set("balance_braces", s + "}".repeat(diff))

        // remove trailing commas before } or ]
        val trailingComma = Regex(",\\s*([}\\]])")
        var tmp = s
        var changed = false
        while (true) {
            val replaced = trailingComma.replace(tmp) {
                changed = true
                it.groupValues[1]
            }
            if (replaced == tmp) break
            tmp = replaced
        }
        if (changed) set("remove_trailing_commas", tmp)

        // coerce nulls for required numeric fields that often break decoding
        val requiredNumbers = linkedMapOf(
            "reps" to "8",
            "weekIndex" to "0",
            "kcal" to "0",
            "proteinGrams" to "0",
            "fatGrams" to "0",
            "carbsGrams" to "0",
        )
        requiredNumbers.forEach { (field, defaultValue) ->
            val regex = Regex("\"$field\"\\s*:\\s*null")
            if (regex.containsMatchIn(s)) {
                set("coerce_null_$field", regex.replace(s, "\"$field\":$defaultValue"))
            }
        }

        normalizeStringArrays(s)?.let { normalized ->
            set("flatten_nested_string_arrays", normalized)
        }

        return RepairResult(s.trim(), fixes)
    }

    private fun normalizeStringArrays(raw: String): String? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null
        val normalized = normalizeElement(root, parentKey = null)
        if (normalized == root) return null
        return json.encodeToString(JsonElement.serializer(), normalized)
    }

    private fun normalizeElement(element: JsonElement, parentKey: String?): JsonElement =
        when (element) {
            is JsonObject -> JsonObject(
                element.mapValues { (key, value) -> normalizeElement(value, key) }
            )

            is JsonArray -> {
                val normalizedItems = element.map { normalizeElement(it, parentKey) }
                if (parentKey in arrayStringFields) {
                    JsonArray(flattenStringLeaves(normalizedItems))
                } else {
                    JsonArray(normalizedItems)
                }
            }

            else -> element
        }

    private fun flattenStringLeaves(items: List<JsonElement>): List<JsonElement> =
        buildList {
            items.forEach { item ->
                val leaves = extractStringLeaves(item)
                if (leaves != null) {
                    addAll(leaves)
                } else {
                    add(item)
                }
            }
        }

    private fun extractStringLeaves(element: JsonElement): List<JsonElement>? =
        when (element) {
            is JsonPrimitive -> if (element.isString) listOf(element) else null
            is JsonArray -> {
                val leaves = mutableListOf<JsonElement>()
                element.forEach { child ->
                    val childLeaves = extractStringLeaves(child) ?: return null
                    leaves += childLeaves
                }
                leaves
            }

            else -> null
        }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
        private val arrayStringFields = setOf("messages", "ingredients", "shoppingList")
    }
}

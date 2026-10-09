package com.vtempe.server.features.ai.data.llm.telemetry

/**
 * Log-safe descriptions of LLM failures (VT-AUD-001).
 *
 * Model output can echo the user's health data, so no text that came from the model may reach
 * the logs — and exception or validation messages are not safe: kotlinx.serialization appends
 * "JSON input: …" to decode errors, and validation errors quote meal names, ingredients and
 * values. Only the error's type, its structural JSON path and normalized error categories are kept.
 */
object SafeDiagnostics {
    private val jsonPath = Regex("""at path:?\s*(\$[\w.\[\]]*)""")
    private val unsafeChars = Regex("""[^a-z\[\]._# -]""")
    private val digits = Regex("""\d+""")
    private val spaces = Regex("""\s+""")

    /** "SerializationException at $.trainingPlan.workouts[0].reps" — never the message text. */
    fun decodeError(error: Throwable?): String {
        if (error == null) return "decode error"
        val type = error::class.simpleName ?: "DecodeError"
        val path = error.message?.let { jsonPath.find(it)?.groupValues?.get(1) }?.take(120)
        return if (path != null) "$type at $path" else type
    }

    /** Distinct categories such as "mon meal[#].ingredients[#]" or "workout[#] repeats exercise". */
    fun validationErrors(errors: List<String>): String =
        errors.map(::category).filter { it.isNotEmpty() }.distinct().take(12)
            .joinToString(", ", prefix = "${errors.size} error(s): ")

    /** The part of a validation message before any quoted or appended value, without numbers. */
    fun category(error: String): String {
        val cut = error.indexOfAny(charArrayOf(':', '\'', '"', '(', '='))
        val head = if (cut >= 0) error.substring(0, cut) else error
        return head.lowercase()
            .replace(digits, "#")
            .replace(unsafeChars, " ")
            .replace(spaces, " ")
            .trim()
            .take(60)
    }
}

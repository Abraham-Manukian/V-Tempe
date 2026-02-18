package com.vtempe.server.features.ai.data.llm.feedback

class FeedbackComposer {
    fun decodeError(message: String?): String =
        "Previous response contained invalid JSON (${message ?: "decode error"}). Return ONLY valid JSON, no commentary."

    fun validationErrors(errors: List<String>): String =
        "Previous response failed validation:\n" +
                errors.take(10).joinToString("\n") { "- $it" } +
                "\nReturn corrected JSON ONLY."
}

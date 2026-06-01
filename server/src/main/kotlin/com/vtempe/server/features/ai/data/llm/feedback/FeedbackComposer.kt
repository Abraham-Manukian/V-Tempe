package com.vtempe.server.features.ai.data.llm.feedback

class FeedbackComposer {
    fun decodeError(message: String?): String =
        "Previous response contained invalid JSON (${message ?: "decode error"}). Return ONLY valid JSON, no commentary."

    fun validationErrors(errors: List<String>, extra: String? = null): String = buildString {
        appendLine("Previous response failed validation:")
        errors.take(10).forEach { appendLine("- $it") }
        if (!extra.isNullOrBlank()) {
            appendLine()
            appendLine("Context / constraints to respect when correcting:")
            appendLine(extra.trim())
        }
        append("Return corrected JSON ONLY.")
    }
}

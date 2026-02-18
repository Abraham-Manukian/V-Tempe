package com.vtempe.server.features.ai.data.llm.pipeline

class FeedbackComposer {
    fun compose(errors: List<String>): String {
        val bullet = errors.take(10).joinToString("\n") { "- $it" }
        return """
            Your previous output was invalid.
            Fix the issues and return ONLY valid JSON, with no commentary.

            Errors:
            $bullet
        """.trimIndent()
    }
}
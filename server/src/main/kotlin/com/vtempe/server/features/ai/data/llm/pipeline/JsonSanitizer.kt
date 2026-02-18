package com.vtempe.server.features.ai.data.llm.pipeline

class JsonSanitizer {

    fun sanitize(raw: String): String {
        var s = raw.trim().replace("\r", "")

        // 1) убрать markdown fences ```json ... ```
        s = stripCodeFences(s)

        // 2) вырезать мусор до первого '{' и после последнего '}'
        s = stripToOuterBraces(s)

        // 3) баланс скобок (минимально безопасно)
        s = balanceBraces(s)

        return s.trim()
    }

    private fun stripCodeFences(s: String): String {
        val trimmed = s.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val start = trimmed.indexOf('\n')
        val end = trimmed.lastIndexOf("```")
        if (start < 0 || end <= start) return trimmed
        return trimmed.substring(start + 1, end).trim()
    }

    private fun stripToOuterBraces(s: String): String {
        val start = s.indexOf('{')
        val end = s.lastIndexOf('}')
        if (start < 0 || end < 0 || end <= start) return s
        return s.substring(start, end + 1)
    }

    private fun balanceBraces(s: String): String {
        var depth = 0
        var inString = false
        var escaped = false
        for (c in s) {
            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> depth--
            }
        }
        return if (depth > 0) s + "}".repeat(depth) else s
    }
}

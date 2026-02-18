package com.vtempe.server.features.ai.data.llm.pipeline

class ResponseExtractor {

    fun extractFirstJsonObject(text: String): String? {
        val src = text.replace("\r", "")
        val start = src.indexOf('{')
        if (start < 0) return null
        val (obj, _) = extractJsonObject(src, start)
        return obj
    }

    fun extractJsonAfterMarker(text: String, marker: String): String? {
        val src = text.replace("\r", "")
        val idx = src.indexOf(marker)
        if (idx < 0) return null
        val start = src.indexOf('{', idx + marker.length)
        if (start < 0) return null
        val (obj, _) = extractJsonObject(src, start)
        return obj
    }

    private fun extractJsonObject(source: String, startIndex: Int): Pair<String, Int> {
        var i = startIndex
        var depth = 0
        var inString = false
        var escaped = false

        while (i < source.length) {
            val c = source[i]

            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
                i++
                continue
            }

            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val endInclusive = i
                        return source.substring(startIndex, endInclusive + 1) to (endInclusive + 1)
                    }
                }
            }
            i++
        }
        return source.substring(startIndex) to source.length
    }
}

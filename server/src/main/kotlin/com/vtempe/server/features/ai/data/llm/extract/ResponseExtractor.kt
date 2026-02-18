package com.vtempe.server.features.ai.data.llm.extract

class ResponseExtractor {

    fun firstJsonObject(raw: String): ExtractionResult {
        val src = raw.replace("\r", "")
        val start = src.indexOf('{')
        if (start < 0) return ExtractionResult.Failure("No '{' found in output")

        val (obj, ok) = extractBalancedObject(src, start)
        return if (ok) ExtractionResult.Success(obj) else ExtractionResult.Failure("Could not extract balanced JSON object")
    }

    fun jsonAfterMarker(raw: String, marker: String): ExtractionResult {
        val src = raw.replace("\r", "")
        val m = src.indexOf(marker)
        if (m < 0) return ExtractionResult.Failure("Expected marker '$marker'")

        val start = src.indexOf('{', m + marker.length)
        if (start < 0) return ExtractionResult.Failure("Marker '$marker' found but no '{' after it")

        val (obj, ok) = extractBalancedObject(src, start)
        return if (ok) ExtractionResult.Success(obj) else ExtractionResult.Failure("Could not extract balanced JSON after '$marker'")
    }

    private fun extractBalancedObject(source: String, startIndex: Int): Pair<String, Boolean> {
        var i = startIndex
        var depth = 0
        var inString = false
        var escaped = false

        while (i < source.length) {
            val c = source[i]

            if (inString) {
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') inString = false
                i++
                continue
            }

            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        val end = i
                        return source.substring(startIndex, end + 1) to true
                    }
                }
            }
            i++
        }

        // Не нашли закрывающую — вернём хвост, дальше sanitizer попробует добить.
        return source.substring(startIndex) to false
    }
}

package com.vtempe.server.features.ai.data.llm.repair

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

        return RepairResult(s.trim(), fixes)
    }
}

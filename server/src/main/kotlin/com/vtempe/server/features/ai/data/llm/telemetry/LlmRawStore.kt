package com.vtempe.server.features.ai.data.llm.telemetry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

class LlmRawStore(
    private val enabled: Boolean,
    baseDir: Path = Paths.get("server", "logs", "llm")
) {
    private val dir: Path = baseDir

    init {
        if (enabled) runCatching { Files.createDirectories(dir) }
    }

    fun write(operation: String, requestId: String?, attempt: Int, stage: String, content: String) {
        if (!enabled) return
        if (content.isBlank()) return

        val safeId = sanitize(requestId ?: "unknown")
        val fileName = "${operation}_${safeId}_attempt${attempt}_${stage}.txt"
        val target = dir.resolve(fileName)

        runCatching {
            Files.write(
                target,
                content.toByteArray(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
            )
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")
}

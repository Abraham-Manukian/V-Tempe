package com.vtempe.server.features.ai.data.llm.telemetry

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug-only dump of raw LLM output to local files. Model output can echo user health data
 * (injuries, allergies, chat replies), so files are never kept indefinitely: anything older than
 * [retention] is deleted at startup and, at most once per [PURGE_INTERVAL], on subsequent writes.
 */
class LlmRawStore(
    private val enabled: Boolean,
    baseDir: Path = Paths.get("logs", "llm"),
    private val retention: Duration = DEFAULT_RETENTION,
    private val clock: Clock = Clock.systemUTC()
) {
    private val dir: Path = baseDir
    private val lastPurgeAtMs = AtomicLong(Long.MIN_VALUE)

    init {
        if (enabled) {
            runCatching { Files.createDirectories(dir) }
            purgeExpired()
        }
    }

    fun write(operation: String, requestId: String?, attempt: Int, stage: String, content: String) {
        if (!enabled) return
        if (content.isBlank()) return

        purgeExpiredIfDue()

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

    /** Deletes every dump file last modified before `now - retention`. Best-effort, never throws. */
    fun purgeExpired() {
        val nowMs = clock.millis()
        lastPurgeAtMs.set(nowMs)
        val cutoffMs = nowMs - retention.toMillis()
        runCatching {
            if (!Files.isDirectory(dir)) return
            Files.newDirectoryStream(dir).use { entries ->
                entries
                    .filter { Files.isRegularFile(it) }
                    .filter { Files.getLastModifiedTime(it).toMillis() < cutoffMs }
                    .forEach { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }

    private fun purgeExpiredIfDue() {
        val last = lastPurgeAtMs.get()
        if (last == Long.MIN_VALUE || clock.millis() - last >= PURGE_INTERVAL.toMillis()) {
            purgeExpired()
        }
    }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    companion object {
        val DEFAULT_RETENTION: Duration = Duration.ofHours(24)
        val PURGE_INTERVAL: Duration = Duration.ofMinutes(10)
    }
}

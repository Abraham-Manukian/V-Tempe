package com.vtempe.server.config

import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object Env {
    private val logger = LoggerFactory.getLogger(Env::class.java)
    private val cache: Map<String, String> by lazy { load() }

    operator fun get(key: String): String? = System.getenv(key) ?: cache[key]

    private fun load(): Map<String, String> {
        val cwd = Paths.get("").toAbsolutePath().normalize()
        val map = mutableMapOf<String, String>()
        // Only the two places we actually run from: repo root (local dev, most gradle tasks)
        // and the server/ module dir (some IDE run configs set cwd there).
        val candidates = listOf(
            cwd.resolve(".env.local"),
            cwd.resolve(".env"),
            cwd.resolve("server/.env.local"),
            cwd.resolve("server/.env")
        )
        candidates.forEach { readFileIfExists(it, map) }
        // Key names (not values) at debug level only \u2014 never print in prod, and never print values.
        if (map.isEmpty()) {
            logger.debug("No env files found (cwd={})", cwd)
        } else {
            logger.debug("Loaded {} env key(s) from local .env file(s)", map.size)
        }
        return map
    }

    private fun readFileIfExists(path: Path, target: MutableMap<String, String>) {
        if (!Files.exists(path)) return
        readFile(path, target)
    }

    private fun readFile(path: Path, target: MutableMap<String, String>) {
        runCatching {
            Files.newBufferedReader(path).use { reader ->
                reader.lineSequence()
                    .map { line -> line.trim().removePrefix("\uFEFF") }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .forEach { cleaned ->
                        val idx = cleaned.indexOf('=')
                        if (idx > 0) {
                            val key = cleaned.substring(0, idx).trim().removePrefix("\uFEFF")
                            val value = cleaned.substring(idx + 1).trim().trim('"')
                            if (key.isNotEmpty() && key !in target) {
                                target[key] = value
                            }
                        }
                    }
            }
            logger.debug("Loaded env file {}", path)
        }.onFailure {
            logger.warn("Failed to read env file {}: {}", path, it.message)
        }
    }
}


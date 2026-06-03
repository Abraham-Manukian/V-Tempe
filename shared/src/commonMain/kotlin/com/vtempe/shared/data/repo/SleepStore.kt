package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.model.SleepEntry
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists per-date sleep durations (minutes) to Settings.
 * Keeps the last 14 days; older entries are pruned automatically.
 */
class SleepStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), Int.serializer())

    /** Record sleep for [date] (ISO "YYYY-MM-DD"). Replaces any existing entry for that day. */
    fun logSleep(date: String, minutes: Int) {
        val current = load().toMutableMap()
        current[date] = minutes.coerceIn(0, 24 * 60)
        // Keep most recent 14 days
        val trimmed = current.entries
            .sortedByDescending { it.key }
            .take(14)
            .associate { it.key to it.value }
        settings.putString(KEY, json.encodeToString(serializer, trimmed))
    }

    /** Minutes logged for [date], or 0 if not recorded. */
    fun getForDate(date: String): Int = load()[date] ?: 0

    /** Most recent [limit] entries sorted newest-first, suitable for sending to the server. */
    fun recentEntries(limit: Int = 7): List<SleepEntry> =
        load().entries
            .sortedByDescending { it.key }
            .take(limit)
            .map { SleepEntry(date = it.key, durationMinutes = it.value) }

    fun clear() = settings.remove(KEY)

    private fun load(): Map<String, Int> =
        settings.getStringOrNull(KEY)
            ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    private companion object {
        const val KEY = "sleep.history.v1"
    }
}

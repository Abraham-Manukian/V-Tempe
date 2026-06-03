package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.model.WeightEntry
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists per-date body weight (kg) to Settings.
 * Also tracks whether the weekly check-in has been shown.
 */
class WeightStore(private val settings: Settings) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), Double.serializer())

    /** Record weight for [date] (ISO "YYYY-MM-DD"). Prunes to most recent 30 entries. */
    fun logWeight(date: String, kg: Double) {
        val current = load().toMutableMap()
        current[date] = kg.coerceIn(20.0, 500.0)
        val trimmed = current.entries
            .sortedByDescending { it.key }
            .take(30)
            .associate { it.key to it.value }
        settings.putString(KEY, json.encodeToString(serializer, trimmed))
        // Mark that the check-in has been done this week so we don't nag again
        settings.putString(KEY_LAST_CHECKIN, mondayOfCurrentWeek())
    }

    /** Latest recorded weight, or null if never logged. */
    fun latestWeight(): Double? =
        load().entries.maxByOrNull { it.key }?.value

    /** Most recent [limit] entries sorted newest-first. */
    fun recentEntries(limit: Int = 8): List<WeightEntry> =
        load().entries
            .sortedByDescending { it.key }
            .take(limit)
            .map { WeightEntry(date = it.key, weightKg = it.value) }

    /**
     * True if the user hasn't logged their weight this calendar week yet.
     * Resets every Monday.
     */
    fun shouldShowCheckin(): Boolean {
        val lastCheckin = settings.getStringOrNull(KEY_LAST_CHECKIN) ?: return true
        return lastCheckin != mondayOfCurrentWeek()
    }

    /** Dismiss the check-in prompt for this week without logging a value. */
    fun dismissCheckin() {
        settings.putString(KEY_LAST_CHECKIN, mondayOfCurrentWeek())
    }

    fun clear() {
        settings.remove(KEY)
        settings.remove(KEY_LAST_CHECKIN)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Returns "YYYY-MM-DD" for Monday of the current week. */
    private fun mondayOfCurrentWeek(): String {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        // DayOfWeek.MONDAY.ordinal == 0, ..., SUNDAY.ordinal == 6
        val daysFromMonday = today.dayOfWeek.ordinal
        val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
        return monday.toString()
    }

    private fun load(): Map<String, Double> =
        settings.getStringOrNull(KEY)
            ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    companion object {
        private const val KEY = "weight.history.v1"
        private const val KEY_LAST_CHECKIN = "weight.checkin.week.v1"
    }
}

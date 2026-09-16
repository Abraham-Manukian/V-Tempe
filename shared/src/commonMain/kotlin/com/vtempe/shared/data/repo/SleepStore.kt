package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.model.SleepEntry
import com.vtempe.shared.domain.model.MAX_SLEEP_NOTE_LENGTH
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.SerializationException
import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists per-date sleep durations (minutes) to Settings.
 * Keeps the last 14 days; older entries are pruned automatically.
 */
class SleepStore(
    private val settings: Settings,
    /** See WorkoutProgressStore's kdoc on the same parameter — same lazy-DI reasoning. */
    private val onLocalChange: (SyncDomain) -> Unit = {}
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), Int.serializer())
    private val notesSerializer = MapSerializer(String.serializer(), String.serializer())

    /** Record sleep for [date] (ISO "YYYY-MM-DD"). Replaces any existing entry for that day. */
    fun logSleep(date: String, minutes: Int, notes: String = getNotesForDate(date)) {
        val current = load().toMutableMap()
        current[date] = minutes.coerceIn(0, 24 * 60)
        // Keep most recent 14 days
        val trimmed = current.entries
            .sortedByDescending { it.key }
            .take(14)
            .associate { it.key to it.value }
        settings.putString(KEY, json.encodeToString(serializer, trimmed))
        val updatedNotes = loadNotes().toMutableMap().apply {
            put(date, notes.trim().take(MAX_SLEEP_NOTE_LENGTH))
            keys.retainAll(trimmed.keys)
        }
        settings.putString(NOTES_KEY, json.encodeToString(notesSerializer, updatedNotes))
        onLocalChange(SyncDomain.SLEEP)
        onLocalChange(SyncDomain.SLEEP_NOTES)
    }

    /** Current local state as the raw JSON string already persisted to [settings]. */
    fun rawSnapshot(): String? = settings.getStringOrNull(KEY)

    /** Overwrites local state with a snapshot pulled from the server. */
    fun restoreRaw(rawJson: String) {
        settings.putString(KEY, rawJson)
    }

    /** Minutes logged for [date], or 0 if not recorded. */
    fun getForDate(date: String): Int = load()[date] ?: 0

    fun getNotesForDate(date: String): String = loadNotes()[date].orEmpty()
    fun rawNotesSnapshot(): String? = settings.getStringOrNull(NOTES_KEY)
    fun restoreNotesRaw(rawJson: String) {
        val notes = (decodeNotes(rawJson) ?: return).entries
            .sortedByDescending { it.key }.take(14)
            .associate { it.key to it.value.trim().take(MAX_SLEEP_NOTE_LENGTH) }
        settings.putString(NOTES_KEY, json.encodeToString(notesSerializer, notes))
    }

    private fun loadNotes(): Map<String, String> = settings.getStringOrNull(NOTES_KEY)
        ?.let(::decodeNotes).orEmpty()

    private fun decodeNotes(raw: String): Map<String, String>? = try {
        json.decodeFromString(notesSerializer, raw)
    } catch (_: SerializationException) {
        Napier.w("Invalid sleep notes snapshot ignored")
        null
    }

    /** Most recent [limit] entries sorted newest-first, suitable for sending to the server. */
    fun recentEntries(limit: Int = 7): List<SleepEntry> {
        val notes = loadNotes()
        return load().entries
            .sortedByDescending { it.key }
            .take(limit)
            .map { SleepEntry(date = it.key, durationMinutes = it.value, notes = notes[it.key].orEmpty()) }
    }

    fun clear() {
        settings.remove(KEY)
        settings.remove(NOTES_KEY)
    }

    private fun load(): Map<String, Int> =
        settings.getStringOrNull(KEY)
            ?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap()) }
            ?: emptyMap()

    private companion object {
        const val KEY = "sleep.history.v1"
        const val NOTES_KEY = "sleep.notes.v1"
    }
}

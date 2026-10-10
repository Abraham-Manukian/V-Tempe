package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.model.SleepEntry
import com.vtempe.shared.domain.model.MAX_SLEEP_NOTE_LENGTH
import com.vtempe.shared.domain.model.SleepBounds
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.SerializationException
import io.github.aakira.napier.Napier
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists per-date sleep durations (minutes) to Settings.
 * Keeps the last [SleepBounds.HISTORY_DAYS] logged days; older entries are pruned automatically.
 * Every entry obeys [SleepBounds] — whether logged here or restored from sync (VT-AUD-039).
 */
class SleepStore(
    private val settings: Settings,
    /** See WorkoutProgressStore's kdoc on the same parameter — same lazy-DI reasoning. */
    private val onLocalChange: (SyncDomain) -> Unit = {},
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = MapSerializer(String.serializer(), Int.serializer())
    private val notesSerializer = MapSerializer(String.serializer(), String.serializer())
    private val _revision = MutableStateFlow(0L)

    /** Changes after every write, restore or clear, so screens can re-read the diary (VT-AUD-044). */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Record sleep for [date] (ISO "YYYY-MM-DD"). Replaces any existing entry for that day. */
    fun logSleep(date: String, minutes: Int, notes: String = getNotesForDate(date)) {
        val day = SleepBounds.parseDate(date) ?: run {
            Napier.w("Sleep for a non-date key ignored")
            return
        }
        val current = load().toMutableMap()
        current[day.toString()] = minutes.coerceIn(SleepBounds.MINUTES)
        val trimmed = newest(current)
        settings.putString(KEY, json.encodeToString(serializer, trimmed))
        val updatedNotes = loadNotes().toMutableMap().apply {
            put(day.toString(), notes.trim().take(MAX_SLEEP_NOTE_LENGTH))
            keys.retainAll(trimmed.keys)
        }
        settings.putString(NOTES_KEY, json.encodeToString(notesSerializer, updatedNotes))
        _revision.value++
        onLocalChange(SyncDomain.SLEEP)
        onLocalChange(SyncDomain.SLEEP_NOTES)
    }

    /** Current local state as the raw JSON string already persisted to [settings]. */
    fun rawSnapshot(): String? = settings.getStringOrNull(KEY)

    /**
     * Replaces local durations with a snapshot pulled from the server (VT-AUD-049). The snapshot is
     * decoded first: an unreadable one leaves the current history untouched and returns false.
     * Entries that break [SleepBounds] are dropped, the rest kept.
     */
    fun restoreRaw(rawJson: String): Boolean {
        val decoded = decode(rawJson) ?: return false
        settings.putString(KEY, json.encodeToString(serializer, newest(decoded)))
        _revision.value++
        return true
    }

    /** Minutes logged for [date], or 0 if not recorded. */
    fun getForDate(date: String): Int = load()[date] ?: 0

    /** Minutes logged for [date], or null when that day has no entry. */
    fun entryFor(date: LocalDate): Int? = load()[date.toString()]

    fun getNotesForDate(date: String): String = loadNotes()[date].orEmpty()
    fun rawNotesSnapshot(): String? = settings.getStringOrNull(NOTES_KEY)

    /** Same contract as [restoreRaw], for notes. */
    fun restoreNotesRaw(rawJson: String): Boolean {
        val notes = (decodeNotes(rawJson) ?: return false).entries
            .filter { SleepBounds.parseDate(it.key) != null }
            .sortedByDescending { it.key }.take(SleepBounds.HISTORY_DAYS)
            .associate { it.key to it.value.trim().take(MAX_SLEEP_NOTE_LENGTH) }
        settings.putString(NOTES_KEY, json.encodeToString(notesSerializer, notes))
        _revision.value++
        return true
    }

    private fun loadNotes(): Map<String, String> = settings.getStringOrNull(NOTES_KEY)
        ?.let(::decodeNotes).orEmpty()

    private fun decodeNotes(raw: String): Map<String, String>? = try {
        json.decodeFromString(notesSerializer, raw)
    } catch (_: SerializationException) {
        Napier.w("Invalid sleep notes snapshot ignored")
        null
    } catch (_: IllegalArgumentException) {
        Napier.w("Invalid sleep notes snapshot ignored")
        null
    }

    /**
     * Nights from the last [SleepBounds.RECENT_DAYS] days, newest first — what describes the user's
     * current recovery. Older history stays stored but is not presented as recent (VT-AUD-040).
     */
    fun recentEntries(limit: Int = 7): List<SleepEntry> {
        val notes = loadNotes()
        val now = today()
        return load().entries
            .filter { SleepBounds.isRecent(it.key, now) }
            .sortedByDescending { it.key }
            .take(limit)
            .map { SleepEntry(date = it.key, durationMinutes = it.value, notes = notes[it.key].orEmpty()) }
    }

    /** True when any night is stored, however old. */
    fun hasEntries(): Boolean = load().isNotEmpty()

    fun clear() {
        settings.remove(KEY)
        settings.remove(NOTES_KEY)
        _revision.value++
    }

    /** Valid entries only: a value stored before validation existed must not reach the coach. */
    private fun load(): Map<String, Int> = settings.getStringOrNull(KEY)?.let(::decode).orEmpty()

    private fun decode(raw: String): Map<String, Int>? = try {
        json.decodeFromString(serializer, raw)
            .mapKeys { (date, _) -> SleepBounds.parseDate(date)?.toString().orEmpty() }
            .filter { (date, minutes) -> SleepBounds.isValid(date, minutes) }
    } catch (_: SerializationException) {
        Napier.w("Invalid sleep snapshot ignored")
        null
    } catch (_: IllegalArgumentException) {
        Napier.w("Invalid sleep snapshot ignored")
        null
    }

    private fun newest(entries: Map<String, Int>): Map<String, Int> = entries.entries
        .sortedByDescending { it.key }
        .take(SleepBounds.HISTORY_DAYS)
        .associate { it.key to it.value }

    private companion object {
        const val KEY = "sleep.history.v1"
        const val NOTES_KEY = "sleep.notes.v1"
    }
}

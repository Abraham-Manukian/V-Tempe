package com.vtempe.shared.data.repo

import com.russhwolf.settings.Settings
import com.vtempe.shared.domain.model.MAX_SLEEP_NOTE_LENGTH
import com.vtempe.shared.domain.repository.SyncDomain
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SleepStoreTest {
    // This store only uses string values; reject accidental use of other Settings APIs.
    private fun settings(): Settings {
        val values = mutableMapOf<String, String>()
        return Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
            when (method.name) {
                "getStringOrNull" -> values[args!![0]]
                "putString" -> { values[args!![0] as String] = args[1] as String; null }
                "remove" -> { values.remove(args!![0]); null }
                else -> error("Unexpected Settings call: ${method.name}")
            }
        } as Settings
    }

    @Test
    fun legacyDurationsRemainReadableAndNotesSurviveRecreation() {
        val settings = settings()
        val changes = mutableListOf<SyncDomain>()
        val store = SleepStore(settings, changes::add)
        store.restoreRaw("""{"2026-09-14":480}""")
        assertEquals("", store.recentEntries().single().notes)
        store.logSleep("2026-09-14", 450, "  Woke twice  ")
        val restored = SleepStore(settings)
        assertEquals(450, restored.getForDate("2026-09-14"))
        assertEquals("Woke twice", restored.recentEntries().single().notes)
        assertEquals(listOf(SyncDomain.SLEEP, SyncDomain.SLEEP_NOTES), changes)
        restored.logSleep("2026-09-14", 460)
        assertEquals("Woke twice", restored.getNotesForDate("2026-09-14"))
    }

    @Test
    fun notesAreBoundedSyncedAndCleared() {
        val store = SleepStore(settings())
        store.logSleep("2026-09-14", 480, "x".repeat(MAX_SLEEP_NOTE_LENGTH + 20))
        val restored = SleepStore(settings())
        restored.restoreRaw(store.rawSnapshot()!!)
        restored.restoreNotesRaw(store.rawNotesSnapshot()!!)
        assertEquals(MAX_SLEEP_NOTE_LENGTH, restored.recentEntries().single().notes.length)
        restored.logSleep("2026-09-14", 480, "")
        assertEquals("", restored.getNotesForDate("2026-09-14"))
        restored.clear()
        assertTrue(restored.recentEntries().isEmpty())
        assertEquals("", restored.getNotesForDate("2026-09-14"))
    }
}

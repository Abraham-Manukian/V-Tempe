package com.vtempe.shared.data.sync

import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.repository.RemoteSyncSnapshot
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * VT-AUD-049: an unreadable snapshot never replaces readable local history (sleep, weight,
 * workout progress). VT-AUD-039/040: restored and stored nights obey the sleep bounds, and only
 * recent nights are offered as current history.
 */
class SnapshotRestoreSafetyTest {
    private val device = TestDevice()

    @AfterTest
    fun tearDown() = device.close()

    @Test
    fun unreadableSnapshotsKeepLocalDataAndAreReported() = runBlocking<Unit> {
        device.sleep.logSleep("2026-10-01", 480, "fine")
        device.weight.logWeight("2026-10-01", 80.0)
        device.workouts.save(WorkoutProgress(workoutId = "w1", notes = "kept"))
        val stored = listOf(device.sleep.rawSnapshot(), device.sleep.rawNotesSnapshot(), device.weight.rawSnapshot(), device.workouts.rawSnapshot())

        val rejected = device.sync.restore(
            RemoteSyncSnapshot(
                mapOf(
                    SyncDomain.SLEEP.wireKey to "not-json",
                    SyncDomain.SLEEP_NOTES.wireKey to "[1,2]",
                    SyncDomain.WEIGHT.wireKey to "{\"2026-10-01\":\"heavy\"}",
                    SyncDomain.WORKOUT_PROGRESS.wireKey to "{broken",
                )
            )
        )

        assertEquals(setOf(SyncDomain.SLEEP, SyncDomain.SLEEP_NOTES, SyncDomain.WEIGHT, SyncDomain.WORKOUT_PROGRESS), rejected)
        assertEquals(480, device.sleep.getForDate("2026-10-01"))
        assertEquals("fine", device.sleep.getNotesForDate("2026-10-01"))
        assertEquals(80.0, device.weight.latestWeight())
        assertEquals("kept", device.workouts.current().getValue("w1").notes)
        // What survives a restart is untouched too, not just the in-memory copy.
        assertEquals(stored, listOf(device.sleep.rawSnapshot(), device.sleep.rawNotesSnapshot(), device.weight.rawSnapshot(), device.workouts.rawSnapshot()))
    }

    @Test
    fun readableSnapshotsReplaceLocalDataAndDropInvalidEntries() = runBlocking<Unit> {
        device.sleep.logSleep("2026-10-01", 480)
        val rejected = device.sync.restore(
            RemoteSyncSnapshot(
                mapOf(
                    SyncDomain.SLEEP.wireKey to """{"2026-10-02":450,"2026-10-03":-120,"not-a-date":2000,"2026-10-04":1441}""",
                    SyncDomain.WEIGHT.wireKey to """{"2026-10-02":79.5,"2026-10-03":-1.0,"bad":80.0}""",
                    SyncDomain.WORKOUT_PROGRESS.wireKey to RemoteSyncSnapshot.EMPTY_PAYLOAD,
                )
            )
        )
        assertTrue(rejected.isEmpty())
        assertEquals(0, device.sleep.getForDate("2026-10-01"), "a valid snapshot replaces local history")
        assertEquals(450, device.sleep.getForDate("2026-10-02"))
        assertEquals(0, device.sleep.getForDate("2026-10-03"))
        assertEquals(null, device.sleep.entryFor(LocalDate(2026, 10, 4)))
        assertEquals(79.5, device.weight.latestWeight())
        assertTrue(device.workouts.current().isEmpty())
    }

    @Test
    fun onlyRecentValidNightsAreOfferedAsCurrentHistory() {
        val store = SleepStore(inMemorySettings(), today = { LocalDate(2026, 10, 10) })
        store.restoreRaw("""{"2000-01-01":60,"2026-09-26":300,"2026-09-27":420,"2026-10-11":480,"2026-10-09":-5}""")
        assertEquals(listOf("2026-09-27"), store.recentEntries().map { it.date })
        assertTrue(store.hasEntries(), "older history is kept, just not called recent")

        store.logSleep("2026-10-09", 3_000)
        assertEquals(listOf("2026-10-09" to 1_440, "2026-09-27" to 420), store.recentEntries().map { it.date to it.durationMinutes })
        store.logSleep("garbage", 300)
        assertFalse(store.recentEntries().any { it.date == "garbage" })
    }
}

package com.vtempe.shared.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.PerformedSet
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.SetResult
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.model.WorkoutSummary
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.training.WorkoutProgressEditor
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** VT-AUD-019 (every set kept), 020 (results follow their exercise), 023 (undo) — real store + SQLite. */
class WorkoutHistoryIntegrityTest {

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        val db = AppDatabase(driver)
        val settings = memorySettings()
        val store = WorkoutProgressStore(settings, db)
        val repo = TrainingRepositoryDb(db, NoAi, AiResponseCache(memorySettings()), store)
        override fun close() = driver.close()

        suspend fun done(workout: Workout, index: Int, reps: Int, kg: Double?, rpe: Double? = null) =
            repo.updateWorkoutProgress(workout.id) {
                WorkoutProgressEditor.markSetDone(it, workout, index, SetResult(reps, kg, rpe))
            }

        suspend fun undo(workout: Workout, index: Int) =
            repo.updateWorkoutProgress(workout.id) { WorkoutProgressEditor.undoLastSet(it, workout, index) }

        fun progress(id: String) = store.current().getValue(id)
        suspend fun summary(): WorkoutSummary = repo.recentWorkoutSummaries(10).single()
    }

    private object NoAi : AiTrainerRepository {
        override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> = DataResult.Failure(DataResult.Reason.Network)
    }

    private fun workout(vararg sets: WorkoutSet) = Workout(id = "w_0_0", date = LocalDate(2026, 10, 5), sets = sets.toList())
    private fun plan(w: Workout) = TrainingPlan(0, listOf(w))

    // ── VT-AUD-019 ─────────────────────────────────────────────────────────────

    @Test
    fun threeIdenticalSetsTripleTheVolumeAndPartialWorkIsKept() = runBlocking {
        Fixture().use { f ->
            val w = workout(WorkoutSet("squat", 10, 20.0, sets = 3))
            f.repo.savePlan(plan(w))

            f.done(w, 0, 10, 20.0)
            f.done(w, 0, 10, 20.0)
            val partial = f.summary()
            assertEquals(400.0, partial.totalVolumeKg)
            assertEquals(2.0 / 3.0, partial.completionRate, 1e-9)
            assertEquals(0, partial.completedItems)

            f.done(w, 0, 10, 20.0)
            val full = f.summary()
            assertEquals(600.0, full.totalVolumeKg)
            assertEquals(1.0, full.completionRate)
            assertEquals(1, full.completedItems)
        }
    }

    @Test
    fun differentSetsAreNotOverwrittenAndTheTopSetIsReported() = runBlocking {
        Fixture().use { f ->
            val w = workout(WorkoutSet("bench", 8, 60.0, sets = 3), WorkoutSet("pushup", 15, null, sets = 2))
            f.repo.savePlan(plan(w))
            f.done(w, 0, 8, 60.0, 7.0)
            f.done(w, 0, 6, 70.0, 8.5)
            f.done(w, 0, 10, 50.0, 9.0)
            f.done(w, 1, 15, null)
            f.repo.logSet(w.id, WorkoutSet("curl", 12, 10.0, 7.0))

            val record = f.progress(w.id).performedSets.single { it.exerciseId == "bench" }
            assertEquals(listOf(60.0, 70.0, 50.0), record.setResults.map { it.weightKg })
            val s = f.summary()
            assertEquals(8 * 60.0 + 6 * 70.0 + 10 * 50.0 + 12 * 10.0, s.totalVolumeKg)
            assertEquals((7.0 + 8.5 + 9.0 + 7.0) / 4, s.averageRpe!!, 1e-9)
            assertEquals(70.0, s.exercises.single { it.exerciseId == "bench" }.weightKg)
            assertNull(s.exercises.single { it.exerciseId == "pushup" }.weightKg)
            assertEquals(4.0 / 5.0, s.completionRate, 1e-9)
        }
    }

    @Test
    fun legacyRecordsStillCountEveryCompletedSet() = runBlocking {
        Fixture().use { f ->
            val w = workout(WorkoutSet("squat", 10, 20.0, sets = 3))
            f.repo.savePlan(plan(w))
            // Written by the previous app version: one record, last values, a count.
            f.store.save(WorkoutProgress(w.id, performedSets = listOf(
                PerformedSet(setIndex = 0, completed = true, completedSetsCount = 3, actualReps = 10, actualWeightKg = 20.0)
            )))
            assertEquals(600.0, f.summary().totalVolumeKg)
        }
    }

    // ── VT-AUD-020 ─────────────────────────────────────────────────────────────

    @Test
    fun replacingAnExerciseKeepsItsResultsOffTheNewOne() = runBlocking {
        Fixture().use { f ->
            val squatPlan = workout(WorkoutSet("squat", 8, 80.0, sets = 3))
            f.repo.savePlan(plan(squatPlan))
            repeat(3) { f.done(squatPlan, 0, 8, 90.0) }

            val benchPlan = workout(WorkoutSet("bench", 8, 60.0, sets = 3))
            f.repo.savePlan(plan(benchPlan)) // same workout id, different exercise

            assertTrue(WorkoutProgressEditor.performedFor(benchPlan, f.progress(benchPlan.id)).isEmpty(), "bench must not look done")
            val s = f.summary()
            assertEquals(0.0, s.completionRate)
            assertEquals(listOf("squat"), s.exercises.map { it.exerciseId }, "the squat history is kept")
            assertEquals(90.0, s.exercises.single().weightKg)
        }
    }

    @Test
    fun reorderedExercisesKeepTheirOwnResults() = runBlocking {
        Fixture().use { f ->
            val before = workout(WorkoutSet("squat", 8, 80.0, sets = 3), WorkoutSet("bench", 8, 60.0, sets = 3))
            f.repo.savePlan(plan(before))
            f.done(before, 1, 8, 65.0)

            val after = workout(WorkoutSet("bench", 8, 60.0, sets = 3), WorkoutSet("squat", 8, 80.0, sets = 3))
            f.repo.savePlan(plan(after))
            val performed = WorkoutProgressEditor.performedFor(after, f.progress(after.id))
            assertEquals(setOf(0), performed.keys)
            assertEquals(65.0, performed.getValue(0).setResults.single().weightKg)

            // Saving the identical plan again changes nothing.
            f.repo.savePlan(plan(after))
            assertEquals(performed, WorkoutProgressEditor.performedFor(after, f.progress(after.id)))
        }
    }

    @Test
    fun legacyRecordsAreBoundToTheirExerciseBeforeThePlanIsReplaced() = runBlocking {
        Fixture().use { f ->
            val squatPlan = workout(WorkoutSet("squat", 8, 80.0, sets = 3))
            f.repo.savePlan(plan(squatPlan))
            f.store.save(WorkoutProgress(squatPlan.id, performedSets = listOf(
                PerformedSet(setIndex = 0, completed = true, completedSetsCount = 3, actualReps = 8, actualWeightKg = 90.0)
            )))

            val benchPlan = workout(WorkoutSet("bench", 8, 60.0, sets = 3))
            f.repo.savePlan(plan(benchPlan))

            val record = f.progress(benchPlan.id).performedSets.single()
            assertEquals("squat", record.exerciseId)
            assertEquals(3, record.setResults.size)
            assertTrue(WorkoutProgressEditor.performedFor(benchPlan, f.progress(benchPlan.id)).isEmpty())
        }
    }

    // ── VT-AUD-023 ─────────────────────────────────────────────────────────────

    @Test
    fun undoRemovesExactlyTheLastSetAndTheCountStaysInRange() = runBlocking {
        Fixture().use { f ->
            val w = workout(WorkoutSet("squat", 8, 80.0, sets = 3))
            f.repo.savePlan(plan(w))
            f.done(w, 0, 8, 80.0)
            f.done(w, 0, 8, 85.0)
            f.undo(w, 0)
            val afterUndo = f.progress(w.id).performedSets.single()
            assertEquals(1, afterUndo.completedSetsCount)
            assertFalse(afterUndo.completed)
            assertEquals(listOf(80.0), afterUndo.setResults.map { it.weightKg })

            repeat(5) { f.done(w, 0, 8, 90.0) } // only two more sets fit
            val full = f.progress(w.id).performedSets.single()
            assertEquals(3, full.completedSetsCount)
            assertTrue(full.completed)

            f.undo(w, 0)
            val reopened = f.progress(w.id).performedSets.single()
            assertEquals(2, reopened.completedSetsCount)
            assertFalse(reopened.completed)

            repeat(3) { f.undo(w, 0) }
            assertTrue(f.progress(w.id).performedSets.isEmpty())
        }
    }

    @Test
    fun undoOnALegacyRecordDecrementsIt() = runBlocking {
        Fixture().use { f ->
            val w = workout(WorkoutSet("squat", 8, 80.0, sets = 3))
            f.repo.savePlan(plan(w))
            f.store.save(WorkoutProgress(w.id, performedSets = listOf(
                PerformedSet(setIndex = 0, completed = false, completedSetsCount = 2, actualReps = 8, actualWeightKg = 80.0)
            )))
            f.undo(w, 0)
            assertEquals(1, f.progress(w.id).performedSets.single().completedSetsCount)
        }
    }

    @Test
    fun concurrentTapsAreNotLost() = runBlocking {
        Fixture().use { f ->
            val w = workout(WorkoutSet("plank", 30, null, sets = 10))
            f.repo.savePlan(plan(w))
            (1..10).map { async { f.done(w, 0, 30, null) } }.awaitAll()
            assertNotNull(f.progress(w.id).performedSets.singleOrNull())
            assertEquals(10, f.progress(w.id).performedSets.single().completedSetsCount)
        }
    }
}

private fun memorySettings(): Settings {
    val values = mutableMapOf<String, String>()
    return Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
        when (method.name) {
            "getStringOrNull" -> values[args!![0] as String]
            "putString" -> { values[args!![0] as String] = args[1] as String; null }
            "remove" -> { values.remove(args!![0] as String); null }
            else -> error("Unexpected Settings call: ${method.name}")
        }
    } as Settings
}

package com.vtempe.shared.data.repo

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Foreign keys are off on this database, so `ON DELETE CASCADE` never fires: deleting workouts
 * must delete their sets too, or the previous user's prescribed loads stay in the file after
 * sign-out, account switch or account deletion. Real SQLite (the JDBC driver also has them off).
 */
class WorkoutSetCleanupTest {

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        val db = AppDatabase(driver)
        val training = TrainingRepositoryDb(db, NoAi, AiResponseCache(memorySettings()), WorkoutProgressStore(memorySettings(), db))
        init {
            // The point of these tests: nothing cascades. Fail loudly if a driver ever turns it on.
            check(driver.executeQuery(null, "PRAGMA foreign_keys", { c -> c.next(); QueryResult.Value(c.getLong(0)) }, 0).value == 0L)
        }
        fun sets(): Long = driver.executeQuery(null, "SELECT COUNT(*) FROM WorkoutSet", { c ->
            c.next(); QueryResult.Value(c.getLong(0)!!)
        }, 0).value
        override fun close() = driver.close()
    }

    private object NoAi : AiTrainerRepository {
        override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> = DataResult.Failure(DataResult.Reason.Network)
    }

    private fun plan(week: Int) = TrainingPlan(
        week,
        listOf(Workout(id = "w_${week}_0", date = LocalDate(2026, 10, 5 + week * 7), sets = listOf(
            WorkoutSet("squat", reps = 5, weightKg = 100.0), WorkoutSet("bench", reps = 5, weightKg = 80.0),
        ))),
    )

    @Test
    fun clearingAllUserDataLeavesNoWorkoutSets() = runBlocking {
        Fixture().use { f ->
            f.training.savePlan(plan(0))
            f.training.savePlan(plan(1))

            ProfileRepositoryDb(f.db).clearAll()

            assertEquals(0, f.sets())
        }
    }

    @Test
    fun deletingFutureWeeksDeletesTheirSetsAndKeepsTheCurrentWeek() = runBlocking {
        Fixture().use { f ->
            f.training.savePlan(plan(0))
            f.training.savePlan(plan(1))
            f.training.savePlan(plan(2))

            f.training.deleteWeeksFrom(1)

            assertEquals(2, f.sets(), "only week 0's two sets remain")
        }
    }

    @Test
    fun aWipeThatFailsHalfwayDeletesNothing() = runBlocking {
        Fixture().use { f ->
            f.training.savePlan(plan(0))
            f.driver.execute(null, """
                CREATE TRIGGER reject_wipe BEFORE DELETE ON Profile BEGIN SELECT RAISE(ABORT, 'injected failure'); END
            """.trimIndent(), 0)
            f.driver.execute(null, "INSERT INTO Profile(id, age, sex, heightCm, weightKg, goal, experienceLevel) VALUES ('p', 30, 'MALE', 180, 80.0, 'MAINTAIN', 2)", 0)

            runCatching { ProfileRepositoryDb(f.db).clearAll() }

            assertEquals(2, f.sets(), "the sets deleted before the failure come back with the rollback")
        }
    }

    @Test
    fun orphansLeftByEarlierVersionsAreSweptOnTheNextWeekInvalidation() = runBlocking {
        Fixture().use { f ->
            f.training.savePlan(plan(0))
            f.driver.execute(null, "DELETE FROM Workout", 0) // what the old cascade-reliant code left behind

            f.training.deleteWeeksFrom(1)

            assertEquals(0, f.sets())
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

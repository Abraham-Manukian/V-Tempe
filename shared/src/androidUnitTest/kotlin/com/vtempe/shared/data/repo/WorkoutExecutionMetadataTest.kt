package com.vtempe.shared.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.data.network.dto.TrainingPlanDto
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.exercise.ExerciseCalibrationKind
import com.vtempe.shared.domain.exercise.ExerciseLibrary
import com.vtempe.shared.domain.exercise.executionKind
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** VT-AUD-022 (unit/bodyweight travel with the set) and VT-AUD-024 (prescribed rest is kept). */
class WorkoutExecutionMetadataTest {

    private object NoAi : AiTrainerRepository {
        override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
        override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> = DataResult.Failure(DataResult.Reason.Network)
    }

    private val wallSit = WorkoutSet("wall_sit", 30, null, 7.0, sets = 3, restSeconds = 60, unit = "SECONDS", bodyweight = true)
    private val squat = WorkoutSet("squat", 5, 100.0, 8.0, sets = 5, restSeconds = 240, unit = "REPS", bodyweight = false)
    private val plan = TrainingPlan(0, listOf(Workout("w_0_0", date = LocalDate(2026, 10, 5), sets = listOf(wallSit, squat))))

    @Test
    fun theServersUnitDecidesHowASetIsPerformed() {
        assertNull(ExerciseLibrary.findByIdOrAlias("wall_sit"), "the app library still lacks wall_sit")
        assertEquals(ExerciseCalibrationKind.DURATION_SECONDS, wallSit.executionKind())
        assertEquals(ExerciseCalibrationKind.WEIGHT_AND_REPS, squat.executionKind())
        assertEquals(ExerciseCalibrationKind.BODYWEIGHT_REPS, WorkoutSet("pushup", 10, unit = "REPS", bodyweight = true).executionKind())
        assertEquals(ExerciseCalibrationKind.DURATION_MINUTES, WorkoutSet("run", 20, unit = "MINUTES").executionKind())
        // Legacy plans without the unit fall back to the library, then to weight/reps.
        assertEquals(ExerciseLibrary.findByIdOrAlias("plank")!!.calibrationKind, WorkoutSet("plank", 30).executionKind())
        assertEquals(ExerciseCalibrationKind.WEIGHT_AND_REPS, WorkoutSet("wall_sit", 30).executionKind())
    }

    @Test
    fun metadataSurvivesDtoJsonAndSqlite() = runBlocking {
        val json = Json { encodeDefaults = true }
        val viaDto = json.decodeFromString(TrainingPlanDto.serializer(), json.encodeToString(TrainingPlanDto.serializer(), TrainingPlanDto.fromDomain(plan))).toDomain()
        assertEquals(plan.workouts.single().sets, viaDto.workouts.single().sets)

        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        driver.use {
            val db = AppDatabase(driver)
            val settings = memorySettings()
            val repo = TrainingRepositoryDb(db, NoAi, AiResponseCache(settings), WorkoutProgressStore(settings, db))
            repo.savePlan(plan)
            assertEquals(plan.workouts.single().sets, repo.observeWorkoutsByWeek(0).first().single().sets)
        }
    }

    @Test
    fun migrationNineToTenAddsNullableColumns() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        driver.use {
            // A version-9 WorkoutSet table, with one legacy row.
            driver.execute(null, "DROP TABLE WorkoutSet", 0)
            driver.execute(null, """CREATE TABLE WorkoutSet(workoutId TEXT NOT NULL, exerciseId TEXT NOT NULL, reps INTEGER NOT NULL,
                weightKg REAL, rpe REAL, sets INTEGER NOT NULL DEFAULT 3)""", 0)
            driver.execute(null, "INSERT INTO Workout(id, weekIndex, label, date) VALUES ('w', 0, '', '2026-10-05')", 0)
            driver.execute(null, "INSERT INTO WorkoutSet(workoutId, exerciseId, reps, weightKg, rpe, sets) VALUES ('w', 'squat', 5, 100.0, 8.0, 5)", 0)
            AppDatabase.Schema.migrate(driver, 9, 10)

            val db = AppDatabase(driver)
            val settings = memorySettings()
            val legacy = TrainingRepositoryDb(db, NoAi, AiResponseCache(settings), WorkoutProgressStore(settings, db))
                .observeWorkoutsByWeek(0).first().single().sets.single()
            assertEquals(WorkoutSet("squat", 5, 100.0, 8.0, sets = 5), legacy)
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
}

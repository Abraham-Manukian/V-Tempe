package com.vtempe.shared.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.data.training.OfflineExercisePool
import com.vtempe.shared.data.training.OfflineTrainingPlanner
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Constraints
import com.vtempe.shared.domain.model.Equipment
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.training.ExerciseRestrictions
import com.vtempe.shared.domain.training.NoSafeTrainingPlanException
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** VT-AUD-018: the device-side workout plan follows days, equipment and injuries. */
class TrainingOfflineSafetyTest {

    private val base = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)
    private val equipmentOf = OfflineExercisePool.associate { it.id to it.equipmentAnyOf }
    private val patternOf = OfflineExercisePool.associateBy { it.id }

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        val db = AppDatabase(driver)
        private val failedAi = object : AiTrainerRepository {
            override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> =
                DataResult.Failure(DataResult.Reason.Network)
            override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> =
                DataResult.Failure(DataResult.Reason.Network)
            override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
            override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> =
                DataResult.Failure(DataResult.Reason.Network)
        }

        fun repo(): TrainingRepositoryDb {
            val settings = memorySettings()
            return TrainingRepositoryDb(db, failedAi, AiResponseCache(settings), WorkoutProgressStore(settings, db))
        }

        override fun close() = driver.close()

        private fun memorySettings(): Settings {
            val values = mutableMapOf<String, String>()
            return Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
                when (method.name) {
                    "getStringOrNull" -> values[args!![0] as String]
                    "getLongOrNull", "getIntOrNull" -> null // no plan epoch yet
                    "putString" -> { values[args!![0] as String] = args[1] as String; null }
                    "remove" -> { values.remove(args!![0] as String); null }
                    else -> error("Unexpected Settings call: ${method.name}")
                }
            } as Settings
        }
    }

    private fun TrainingPlan.ids() = workouts.flatMap { w -> w.sets.map { it.exerciseId } }

    @Test
    fun homeProfileWithoutEquipmentGetsOnlyBodyweightWorkOnItsSelectedDay() = runBlocking {
        Fixture().use { f ->
            val profile = base.copy(trainingMode = "HOME", weeklySchedule = mapOf("Wed" to true))
            val plan = f.repo().generatePlan(profile, 2)

            assertEquals(1, plan.workouts.size)
            assertEquals(DayOfWeek.WEDNESDAY, plan.workouts.single().date.dayOfWeek)
            assertTrue(plan.ids().all { equipmentOf.getValue(it).isEmpty() }, plan.ids().toString())
            assertTrue(plan.workouts.flatMap { it.sets }.all { it.weightKg == null }, "no invented loads")
            assertTrue(f.repo().hasPlan(2))
        }
    }

    @Test
    fun ownedEquipmentIsUsedAndGymModeUnlocksTheBarbell() {
        val planner = OfflineTrainingPlanner()
        val today = LocalDate(2026, 10, 5) // a Monday
        val days = mapOf("Mon" to true, "Wed" to true, "Fri" to true)

        val dumbbells = planner.buildPlan(
            base.copy(trainingMode = "HOME", equipment = Equipment(listOf("Гантели")), weeklySchedule = days), 0, today
        )
        assertTrue(dumbbells.ids().all { id -> equipmentOf.getValue(id).let { it.isEmpty() || "dumbbells" in it } }, dumbbells.ids().toString())
        assertTrue(dumbbells.ids().any { "dumbbells" in equipmentOf.getValue(it) }, "owned dumbbells should be used")

        val gym = planner.buildPlan(base.copy(trainingMode = "GYM", experienceLevel = 3, weeklySchedule = days), 0, today)
        assertTrue("squat" in gym.ids() || "deadlift" in gym.ids(), gym.ids().toString())
        assertEquals(listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), gym.workouts.map { it.date.dayOfWeek })
    }

    @Test
    fun injuriesAreHonouredForEveryEquipmentSetup() {
        val planner = OfflineTrainingPlanner()
        val today = LocalDate(2026, 10, 5)
        val injurySets = listOf(
            listOf("knee pain"), listOf("lower back hernia"), listOf("shoulder"), listOf("wrist"), listOf("tennis elbow"),
            listOf("neck"), listOf("ankle"), listOf("hip"), listOf("болит колено", "грыжа поясницы"),
        )
        val setups = listOf("HOME" to emptyList(), "HOME" to listOf("Dumbbells", "Pull-up bar"), "GYM" to emptyList<String>())
        for (injuries in injurySets) for ((mode, equipment) in setups) {
            val profile = base.copy(
                trainingMode = mode, equipment = Equipment(equipment), constraints = Constraints(injuries = injuries),
                weeklySchedule = mapOf("Mon" to true, "Thu" to true), sessionDurationMins = 60,
            )
            val restrictions = ExerciseRestrictions.forInjuries(injuries)
            val plan = planner.buildPlan(profile, 0, today)
            assertTrue(plan.workouts.isNotEmpty(), "$injuries / $mode")
            plan.ids().forEach { id ->
                val exercise = patternOf.getValue(id)
                assertTrue(restrictions.allows(id, exercise.primary, exercise.secondary), "$id for $injuries / $mode")
            }
        }
    }

    @Test
    fun sessionLengthAndLevelShapeTheWorkout() {
        val planner = OfflineTrainingPlanner()
        val today = LocalDate(2026, 10, 5)
        val short = planner.buildPlan(base.copy(sessionDurationMins = 20, weeklySchedule = mapOf("Mon" to true)), 0, today)
        val long = planner.buildPlan(base.copy(sessionDurationMins = 75, weeklySchedule = mapOf("Mon" to true)), 0, today)
        assertTrue(short.workouts.single().sets.size < long.workouts.single().sets.size)
        val beginner = planner.buildPlan(base.copy(experienceLevel = 1, trainingMode = "GYM"), 0, today)
        assertTrue(beginner.ids().all { patternOf.getValue(it).difficulty <= 2 }, beginner.ids().toString())
        assertEquals(3, beginner.workouts.size, "no selected days → Mon/Wed/Fri")
    }

    @Test
    fun impossibleRestrictionsAreRefusedAndNothingIsStored() = runBlocking {
        val pool = OfflineExercisePool.filter { it.id == "plank" } // only one exercise, banned for wrists
        val planner = OfflineTrainingPlanner(pool)
        assertFailsWith<NoSafeTrainingPlanException> {
            planner.buildPlan(base.copy(constraints = Constraints(injuries = listOf("wrist"))), 0, LocalDate(2026, 10, 5))
        }
        Fixture().use { f ->
            val settings = f.repo() // default planner works for a normal profile
            assertTrue(settings.generatePlan(base, 0).workouts.isNotEmpty())
            val refusing = TrainingRepositoryDb(
                f.db, object : AiTrainerRepository {
                    override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> =
                        DataResult.Failure(DataResult.Reason.Network)
                    override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> =
                        DataResult.Failure(DataResult.Reason.Network)
                    override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
                    override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> =
                        DataResult.Failure(DataResult.Reason.Network)
                },
                AiResponseCache(memorylessSettings()), WorkoutProgressStore(memorylessSettings(), f.db), planner
            )
            assertFailsWith<NoSafeTrainingPlanException> {
                refusing.generatePlan(base.copy(constraints = Constraints(injuries = listOf("wrist"))), 5)
            }
            assertFalse(refusing.hasPlan(5))
        }
    }

    private fun memorylessSettings(): Settings =
        Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, _ ->
            when (method.name) {
                "getStringOrNull" -> null
                "getLongOrNull", "getIntOrNull" -> null // no plan epoch yet
                "putString", "remove" -> null
                else -> error("Unexpected Settings call: ${method.name}")
            }
        } as Settings
}

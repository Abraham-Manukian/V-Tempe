package com.vtempe.shared.data.repo

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** VT-AUD-033 (week replaced atomically) and VT-AUD-034 (no unreachable meal rows). Real SQLite. */
class NutritionStorageAtomicityTest {

    private class Fixture : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        val db = AppDatabase(driver)
        fun repo() = NutritionRepositoryDb(db, NoAi, English, AiResponseCache(memorySettings()))
        fun count(table: String): Long = driver.executeQuery(null, "SELECT COUNT(*) FROM $table", { c ->
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

    private object English : LanguagePreferences {
        override fun getLanguageTag() = "en"
        override fun setLanguageTag(tag: String?) {}
    }

    private fun meal(name: String) = Meal(name, listOf("$name ingredient"), 300, Macros(20, 10, 30, 300))
    private fun plan(week: Int, vararg names: String) = NutritionPlan(week, mapOf("Mon" to names.map(::meal)), names.map { "$it ingredient" })

    @Test
    fun failedReplacementKeepsThePreviousCompleteWeek() = runBlocking {
        Fixture().use { f ->
            val repo = f.repo()
            repo.registerActiveWeek(0)
            repo.savePlan(plan(0, "Old breakfast", "Old lunch"))
            f.driver.execute(null, """
                CREATE TRIGGER reject_meal BEFORE INSERT ON Meal
                WHEN NEW.name = 'Reject' BEGIN SELECT RAISE(ABORT, 'injected failure'); END
            """.trimIndent(), 0)

            assertFailsWith<Exception> { repo.savePlan(plan(0, "New breakfast", "Reject")) }

            assertEquals(listOf("Old breakfast", "Old lunch"), repo.observePlan().first()!!.mealsByDay.getValue("Mon").map { it.name })
            val reloaded = f.repo()
            assertTrue(reloaded.setActiveWeek(0))
            assertEquals(listOf("Old breakfast", "Old lunch"), reloaded.observePlan().first()!!.mealsByDay.getValue("Mon").map { it.name })
            assertEquals(2, f.count("Meal"))
        }
    }

    @Test
    fun successfulReplacementIsVisibleInFull() = runBlocking {
        Fixture().use { f ->
            val repo = f.repo()
            repo.registerActiveWeek(0)
            repo.savePlan(plan(0, "Old breakfast", "Old lunch"))
            repo.savePlan(plan(0, "New breakfast", "New lunch", "New dinner"))
            val reloaded = f.repo()
            reloaded.setActiveWeek(0)
            assertEquals(listOf("New breakfast", "New lunch", "New dinner"), reloaded.observePlan().first()!!.mealsByDay.getValue("Mon").map { it.name })
        }
    }

    @Test
    fun shrinkingAndDeletingAWeekLeavesNoMealRows() = runBlocking {
        Fixture().use { f ->
            val repo = f.repo()
            repo.savePlan(plan(0, "Breakfast", "Lunch"))
            repo.savePlan(plan(0, "Breakfast"))
            assertEquals(1, f.count("Meal"))
            assertEquals(1, f.count("MealIngredient"))

            repo.savePlan(plan(1, "Next week"))
            repo.deleteWeeksFrom(0)
            assertFalse(repo.hasPlan(0))
            assertEquals(0, f.count("Meal"))
            assertEquals(0, f.count("MealIngredient"))
            assertEquals(0, f.count("MealByDay"))
        }
    }

    @Test
    fun otherWeeksAreUntouchedAndOldOrphansAreCleanedUp() = runBlocking {
        Fixture().use { f ->
            val repo = f.repo()
            repo.savePlan(plan(0, "Week0"))
            repo.savePlan(plan(1, "Week1"))
            // Leftovers written by the previous version: a meal no week links to.
            f.driver.execute(null, "INSERT INTO Meal(id, name, kcal, protein, fat, carbs) VALUES ('m_0_Tue_5', 'Orphan', 1, 1, 1, 1)", 0)
            f.driver.execute(null, "INSERT INTO MealIngredient(mealId, ingredient) VALUES ('m_0_Tue_5', 'orphan')", 0)

            repo.deleteWeeksFrom(1)
            assertTrue(repo.hasPlan(0))
            assertFalse(repo.hasPlan(1))
            assertEquals(1, f.count("Meal"))
            assertEquals(1, f.count("MealIngredient"))
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

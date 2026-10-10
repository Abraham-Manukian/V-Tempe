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

/** VT-AUD-032: the aggregated shopping list survives a reload; migration 8 → 9 is additive. */
class ShoppingListPersistenceTest {

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

    private fun repo(db: AppDatabase) = NutritionRepositoryDb(db, NoAi, English, AiResponseCache(memorySettings()))
    private fun meal(name: String) = Meal(name, listOf("rice 100 g"), 200, Macros(10, 4, 31, 200))

    @Test
    fun aggregatedListIsTheSameAfterAReload() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        driver.use {
            val db = AppDatabase(driver)
            val plan = NutritionPlan(0, mapOf("Mon" to listOf(meal("Breakfast"), meal("Lunch"))), listOf("Rice — 200 г"))
            repo(db).savePlan(plan)

            val restored = repo(db)
            restored.setActiveWeek(0)
            assertEquals(listOf("Rice — 200 г"), restored.observePlan().first()!!.shoppingList)

            // Replacing the week replaces its list too; deleting it removes it.
            repo(db).savePlan(plan.copy(shoppingList = listOf("Rice — 100 г", "Salt")))
            val again = repo(db).also { it.setActiveWeek(0) }
            assertEquals(listOf("Rice — 100 г", "Salt"), again.observePlan().first()!!.shoppingList)
            again.deleteWeeksFrom(0)
            assertEquals(0L, driver.executeQuery(null, "SELECT COUNT(*) FROM ShoppingItem", { c -> c.next(); QueryResult.Value(c.getLong(0)!!) }, 0).value)
        }
    }

    @Test
    fun weeksSavedBeforeTheMigrationKeepTheirIngredientList() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        driver.use {
            val db = AppDatabase(driver)
            repo(db).savePlan(NutritionPlan(0, mapOf("Mon" to listOf(meal("Breakfast"))), listOf("Rice — 100 г")))
            driver.execute(null, "DELETE FROM ShoppingItem", 0) // as if written by the previous version
            val restored = repo(db).also { it.setActiveWeek(0) }
            assertEquals(listOf("rice 100 g"), restored.observePlan().first()!!.shoppingList)
        }
    }

    @Test
    fun migrationEightToNineAddsTheTableWithoutTouchingData() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        driver.use {
            val db = AppDatabase(driver)
            repo(db).savePlan(NutritionPlan(0, mapOf("Mon" to listOf(meal("Breakfast"))), emptyList()))
            driver.execute(null, "DROP TABLE ShoppingItem", 0) // a version-8 database
            AppDatabase.Schema.migrate(driver, 8, AppDatabase.Schema.version)

            val restored = repo(db).also { it.setActiveWeek(0) }
            assertEquals(listOf("Breakfast"), restored.observePlan().first()!!.mealsByDay.getValue("Mon").map { it.name })
            repo(db).savePlan(NutritionPlan(0, mapOf("Mon" to listOf(meal("Breakfast"))), listOf("Rice — 100 г")))
            assertEquals(listOf("Rice — 100 г"), repo(db).also { it.setActiveWeek(0) }.observePlan().first()!!.shoppingList)
            assertEquals(9L, AppDatabase.Schema.version)
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

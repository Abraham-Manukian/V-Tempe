package com.vtempe.shared.data.repo

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.data.network.dto.NutritionPlanDto
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.dayTotals
import com.vtempe.shared.domain.model.hasNutrition
import com.vtempe.shared.domain.model.isApproximate
import com.vtempe.shared.domain.model.weekTotals
import com.vtempe.shared.domain.nutrition.EnergyTargets
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * VT-AUD-036: without an AI plan the menu names the food and states one daily target — no
 * invented per-dish numbers — and that target survives storage, the cache and the chat round trip.
 */
class ApproximateMenuTest {

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

    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
    private val db = AppDatabase(driver)
    private val profile = Profile("p", 30, Sex.FEMALE, 165, 62.0, Goal.MAINTAIN, 1)
    private fun repo() = NutritionRepositoryDb(db, NoAi, English, AiResponseCache(memorySettings()))
    private fun meal(name: String, kcal: Int) = Meal(name, listOf("rice"), kcal, Macros(10, 5, kcal / 8, kcal))
    private fun targetRows() =
        driver.executeQuery(null, "SELECT COUNT(*) FROM NutritionDailyTarget", { c -> c.next(); QueryResult.Value(c.getLong(0)!!) }, 0).value

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun theOfflineMenuStatesTheProfileTargetAndKeepsItAfterAReload() = runBlocking {
        val plan = repo().generatePlan(profile, weekIndex = 0)

        val target = EnergyTargets.forProfile(profile)
        assertEquals(target, plan.dailyTarget)
        assertTrue(plan.mealsByDay.values.flatten().none { it.hasNutrition }, "no invented per-dish numbers")

        val reloaded = repo().also { it.setActiveWeek(0) }.observePlan().first()!!
        assertTrue(reloaded.isApproximate)
        assertEquals(target, reloaded.dayTotals("Mon"))
        assertEquals(target.kcal * reloaded.mealsByDay.size, reloaded.weekTotals().kcal)
    }

    @Test
    fun aRegularPlanReplacingTheWeekDropsTheTarget() = runBlocking {
        repo().generatePlan(profile, weekIndex = 0)
        repo().savePlan(NutritionPlan(0, mapOf("Mon" to listOf(meal("Bowl", 600), meal("Soup", 400))), listOf("rice")))

        val reloaded = repo().also { it.setActiveWeek(0) }.observePlan().first()!!
        assertFalse(reloaded.isApproximate)
        assertEquals(1000, reloaded.dayTotals("Mon")!!.kcal, "a regular plan totals its meals")
        assertNull(reloaded.dayTotals("Tue"), "no meals, no totals")
        assertEquals(0L, targetRows())
    }

    @Test
    fun deletingWeeksRemovesTheirTargets() = runBlocking {
        repo().generatePlan(profile, weekIndex = 0)
        repo().generatePlan(profile, weekIndex = 1)

        repo().deleteWeeksFrom(1)

        assertEquals(1L, targetRows())
    }

    @Test
    fun theTargetSurvivesTheCacheAndChatWireFormat() {
        val plan = NutritionPlan(0, mapOf("Mon" to listOf(Meal("Oats", listOf("oats"), 0, Macros(0, 0, 0, 0)))), listOf("oats"), Macros(120, 60, 200, 1820))
        val json = Json { ignoreUnknownKeys = true }

        val wire = json.encodeToString(NutritionPlanDto.serializer(), NutritionPlanDto.fromDomain(plan))
        val back = json.decodeFromString(NutritionPlanDto.serializer(), wire).toDomain()

        assertEquals(plan.dailyTarget, back.dailyTarget)
        // A regular plan from the server (no field) stays regular.
        val serverPlan = """{"weekIndex":0,"mealsByDay":{"Mon":[{"name":"Bowl","ingredients":["rice"],"kcal":500,"macros":{"proteinGrams":30,"fatGrams":15,"carbsGrams":60,"kcal":500}}]}}"""
        assertNull(json.decodeFromString(NutritionPlanDto.serializer(), serverPlan).toDomain().dailyTarget)
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

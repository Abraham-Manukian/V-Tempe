package com.vtempe.shared.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.model.Constraints
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.nutrition.FoodRestrictions
import com.vtempe.shared.domain.nutrition.NoSafeMealPlanException
import com.vtempe.shared.domain.nutrition.planViolations
import com.vtempe.shared.domain.repository.AiTrainerRepository
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

/** VT-AUD-025: the device-side meal plan honours allergies and diet. In-memory SQLite, fake AI. */
class NutritionOfflineSafetyTest {

    private val baseProfile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 1)

    private class Fixture(language: String, private val aiPlan: NutritionPlan? = null) : AutoCloseable {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        val db = AppDatabase(driver)
        private val ai = object : AiTrainerRepository {
            override suspend fun generateTrainingPlan(profile: Profile, weekIndex: Int): DataResult<TrainingPlan> =
                DataResult.Failure(DataResult.Reason.Network)
            override suspend fun generateNutritionPlan(profile: Profile, weekIndex: Int): DataResult<NutritionPlan> =
                aiPlan?.let { DataResult.Success(it) } ?: DataResult.Failure(DataResult.Reason.Network)
            override suspend fun getSleepAdvice(profile: Profile): DataResult<Advice> = DataResult.Failure(DataResult.Reason.Network)
            override suspend fun bootstrap(profile: Profile, weekIndex: Int): DataResult<CoachBundle> =
                DataResult.Failure(DataResult.Reason.Network)
        }
        private val languagePrefs = object : LanguagePreferences {
            override fun getLanguageTag() = language
            override fun setLanguageTag(tag: String?) {}
        }
        fun repo() = NutritionRepositoryDb(db, ai, languagePrefs, AiResponseCache(memorySettings()))
        override fun close() = driver.close()

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

    private fun NutritionPlan.text() =
        (mealsByDay.values.flatten().flatMap { it.ingredients + it.name } + shoppingList).map { it.lowercase() }

    @Test
    fun coldOfflinePlanHonoursMilkAllergyAndVeganDiet() = runBlocking {
        Fixture("en").use { f ->
            val profile = baseProfile.copy(allergies = listOf("milk"), dietaryPreferences = listOf("vegan"))
            val plan = f.repo().generatePlan(profile, 0)

            val text = plan.text()
            assertTrue(text.none { "milk" in it || "chicken" in it || "salmon" in it || "honey" in it || "egg" in it }, text.toString())
            assertTrue(FoodRestrictions.from(profile).planViolations(plan).isEmpty())
            assertEquals(7, plan.mealsByDay.size)
            assertTrue(plan.mealsByDay.values.all { it.isNotEmpty() })

            // The compatible plan is stored and restored unchanged.
            val restored = f.repo()
            assertTrue(restored.setActiveWeek(0))
            assertEquals(plan.mealsByDay, restored.observePlan().first()!!.mealsByDay)
        }
    }

    @Test
    fun everySupportedRestrictionAndCombinationIsHonoured() = runBlocking {
        val cases = listOf(
            listOf("milk allergy") to emptyList(),
            listOf("lactose intolerance") to emptyList(),
            listOf("peanut allergy") to emptyList(),
            listOf("tree nut allergy") to emptyList(),
            listOf("gluten") to emptyList(),
            listOf("soy allergy") to emptyList(),
            listOf("egg allergy") to emptyList(),
            listOf("fish allergy") to emptyList(),
            listOf("shellfish") to emptyList(),
            listOf("sesame") to emptyList(),
            emptyList<String>() to listOf("vegan"),
            emptyList<String>() to listOf("vegetarian"),
            listOf("gluten", "soy allergy") to listOf("vegan"),
            listOf("milk allergy", "rice allergy", "egg allergy") to emptyList(),
        )
        for (language in listOf("en", "ru")) {
            for ((allergies, diet) in cases) {
                Fixture(language).use { f ->
                    val profile = baseProfile.copy(allergies = allergies, dietaryPreferences = diet)
                    val plan = f.repo().generatePlan(profile, 0)
                    val label = "$language $allergies $diet"
                    assertTrue(FoodRestrictions.from(profile).planViolations(plan).isEmpty(), label)
                    assertTrue(plan.mealsByDay.values.all { it.isNotEmpty() }, label)
                }
            }
        }
    }

    @Test
    fun hiddenAllergensInTemplatesAreKnownToThePlanner() = runBlocking {
        // Granola, bulgur, teriyaki sauce and oats contain/carry gluten without a keyword for it.
        Fixture("en").use { f ->
            val plan = f.repo().generatePlan(baseProfile.copy(allergies = listOf("gluten", "soy allergy")), 0)
            val text = plan.text()
            assertTrue(text.none { "granola" in it || "bulgur" in it || "teriyaki" in it || "oats" in it }, text.toString())
        }
    }

    @Test
    fun conflictingRestrictionsAreRefusedAndNothingIsStored() = runBlocking {
        val everything = listOf(
            "oats", "berries", "chicken", "quinoa", "broccoli", "salmon", "rice", "yogurt", "granola", "beef",
            "buckwheat", "turkey", "potatoes", "eggs", "pepper", "cod", "bulgur", "apple", "nuts", "pasta",
            "banana", "tofu", "noodles", "kefir", "tuna", "lentils", "chickpeas", "beans", "millet", "carrot",
            "zucchini", "tomato", "spinach", "lettuce", "olives", "asparagus", "greens", "vegetables", "herbs",
            "pumpkin", "cucumber", "sirloin"
        )
        Fixture("en").use { f ->
            val repo = f.repo()
            assertFailsWith<NoSafeMealPlanException> { repo.generatePlan(baseProfile.copy(allergies = everything), 0) }
            assertFalse(repo.hasPlan(0))
        }
    }

    @Test
    fun planWithoutRestrictionsIsUnchanged() = runBlocking {
        Fixture("en").use { f ->
            val plan = f.repo().generatePlan(baseProfile, 0)
            assertEquals(
                listOf("Oatmeal with Berries", "Chicken Quinoa Bowl", "Salmon with Brown Rice"),
                plan.mealsByDay.getValue("Mon").map { it.name }
            )
            assertEquals(listOf("Oat Pancakes", "Tuna Salad", "Baked Cod"), plan.mealsByDay.getValue("Sun").map { it.name })
        }
    }

    @Test
    fun staleAiPlanThatBreaksCurrentRestrictionsIsReplaced() = runBlocking {
        val stale = NutritionPlan(
            weekIndex = 0,
            mealsByDay = mapOf("Mon" to listOf(Meal("Milk porridge", listOf("milk", "oats"), 400, Macros(20, 10, 50, 400)))),
            shoppingList = listOf("milk", "oats")
        )
        Fixture("en", aiPlan = stale).use { f ->
            val profile = baseProfile.copy(constraints = Constraints(healthNotes = listOf("milk allergy")))
            val plan = f.repo().generatePlan(profile, 0)
            assertTrue(plan.text().none { "milk" in it }, plan.text().toString())
            assertEquals(7, plan.mealsByDay.size)
        }
    }

    @Test
    fun compliantAiPlanIsKept() = runBlocking {
        val aiPlan = NutritionPlan(
            weekIndex = 0,
            mealsByDay = mapOf("Mon" to listOf(Meal("Rice bowl", listOf("rice", "broccoli"), 400, Macros(20, 10, 50, 400)))),
            shoppingList = listOf("rice", "broccoli")
        )
        Fixture("en", aiPlan = aiPlan).use { f ->
            val plan = f.repo().generatePlan(baseProfile.copy(allergies = listOf("milk allergy")), 0)
            assertEquals(aiPlan, plan)
        }
    }
}

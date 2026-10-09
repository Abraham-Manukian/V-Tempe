package com.vtempe.shared.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.NutritionPlanDto
import com.vtempe.shared.data.network.dto.TrainingPlanDto
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.repository.AiModelPreferences
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.util.DataResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** VT-AUD-021: an offline fallback is served only for the same week, locale and profile. MockEngine only. */
class OfflineCacheIdentityTest {

    private var online = true
    private val json = Json { encodeDefaults = true }
    private val profile = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)

    private val training0 = TrainingPlan(0, listOf(Workout("w_0_0", date = LocalDate(2026, 9, 1), sets = listOf(WorkoutSet("squat", 8, 60.0)))))
    private val nutrition0 = NutritionPlan(0, mapOf("Mon" to listOf(Meal("Milk porridge", listOf("milk", "oats"), 400, Macros(20, 10, 50, 400)))), listOf("milk", "oats"))

    private fun repository(): Pair<NetworkAiTrainerRepository, AiResponseCache> {
        val engine = MockEngine { request ->
            if (!online) throw IOException("offline")
            val body = when {
                request.url.encodedPath.endsWith("/ai/training") -> json.encodeToString(TrainingPlanDto.fromDomain(training0))
                else -> json.encodeToString(NutritionPlanDto.fromDomain(nutrition0))
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val http = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val settings = memorySettings()
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
        val cache = AiResponseCache(settings)
        val repo = NetworkAiTrainerRepository(
            ApiClient(http, "http://test"),
            object : LanguagePreferences { override fun getLanguageTag() = "en"; override fun setLanguageTag(tag: String?) {} },
            object : AiModelPreferences { override fun getAiModelMode() = AiModelMode.FREE; override fun setAiModelMode(mode: AiModelMode) {} },
            cache,
            WorkoutProgressStore(settings, AppDatabase(driver)),
            SleepStore(settings),
            WeightStore(settings),
        )
        return repo to cache
    }

    @Test
    fun newWeekOfflineIsAFailureNotLastWeeksPlan() = runBlocking<Unit> {
        val (repo, _) = repository()
        assertIs<DataResult.Success<TrainingPlan>>(repo.generateTrainingPlan(profile, 0))
        online = false
        assertIs<DataResult.Failure>(repo.generateTrainingPlan(profile, 1))
        assertIs<DataResult.Failure>(repo.generateNutritionPlan(profile, 1))
    }

    @Test
    fun sameWeekAndProfileStillUsesTheCache() = runBlocking<Unit> {
        val (repo, _) = repository()
        repo.generateTrainingPlan(profile, 0)
        online = false
        val cached = repo.generateTrainingPlan(profile, 0)
        assertIs<DataResult.Success<TrainingPlan>>(cached)
        assertTrue(cached.fromCache)
        assertEquals(0, cached.data.weekIndex)
    }

    @Test
    fun changedRestrictionsInvalidateTheCachedPlan() = runBlocking<Unit> {
        val (repo, _) = repository()
        repo.generateNutritionPlan(profile, 0)
        online = false
        assertIs<DataResult.Failure>(repo.generateNutritionPlan(profile.copy(allergies = listOf("milk")), 0))
    }

    @Test
    fun aLocalSaveOfTheSameWeekKeepsTheFallbackUsable() = runBlocking<Unit> {
        val (repo, cache) = repository()
        repo.generateTrainingPlan(profile, 0)
        val edited = training0.copy(workouts = training0.workouts.map { it.copy(label = "edited") })
        cache.storeTraining(TrainingPlanDto.fromDomain(edited)) // what savePlan does after a chat edit
        online = false
        val cached = repo.generateTrainingPlan(profile, 0)
        assertIs<DataResult.Success<TrainingPlan>>(cached)
        assertEquals("edited", cached.data.workouts.single().label)

        cache.storeTraining(TrainingPlanDto.fromDomain(training0.copy(weekIndex = 3))) // another week saved locally
        assertIs<DataResult.Failure>(repo.generateTrainingPlan(profile, 0))
    }

    private fun memorySettings(): Settings {
        val values = mutableMapOf<String, Any>()
        return Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
            when (method.name) {
                "getStringOrNull" -> values[args!![0] as String] as String?
                "putString" -> { values[args!![0] as String] = args[1] as String; null }
                "remove" -> { values.remove(args!![0] as String); null }
                else -> error("Unexpected Settings call: ${method.name}")
            }
        } as Settings
    }
}

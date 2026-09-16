package com.vtempe.shared.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.data.account.DeviceUserData
import com.vtempe.shared.data.account.SettingsLocalDataOwnerStore
import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.repo.AiResponseCache
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.data.repo.ExerciseCalibrationSettingsRepository
import com.vtempe.shared.data.repo.NetworkSyncRepository
import com.vtempe.shared.data.repo.ProfileRepositoryDb
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.data.repo.WorkoutProgressStore
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.account.AccountDataCoordinator
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.repository.ChatMessage
import com.vtempe.shared.domain.repository.SyncDomain
import com.vtempe.shared.data.network.dto.SyncBlobDto
import com.vtempe.shared.data.network.dto.SyncPullResponseDto
import com.vtempe.shared.data.network.dto.SyncPushRequestDto
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.AuthUser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.lang.reflect.Proxy
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings backed by a plain map; covers the typed get/put/remove calls the stores use. */
internal fun inMemorySettings(): Settings {
    val values = mutableMapOf<String, Any>()
    return Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
        val name = method.name
        when {
            name == "remove" -> { values.remove(args!![0]); null }
            name == "clear" -> { values.clear(); null }
            name == "hasKey" -> values.containsKey(args!![0])
            name == "getKeys" -> values.keys.toSet()
            name == "getSize" -> values.size
            name.startsWith("put") -> { values[args!![0] as String] = args[1]; null }
            name.endsWith("OrNull") -> values[args!![0]]
            name.startsWith("get") -> values[args!![0]] ?: args!![1]
            else -> error("Unexpected Settings call: $name")
        }
    } as Settings
}

/** One phone: real stores on in-memory SQLite/Settings, real sync and coordinator, fake Firebase and server. */
internal class TestDevice {
    private val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).also { AppDatabase.Schema.create(it) }
    private val db = AppDatabase(driver)
    val settings = inMemorySettings()
    val server = FakeSyncServer()
    val auth = FakeAuthRepository()
    val owner = SettingsLocalDataOwnerStore(settings)
    private val pendingPushes = mutableListOf<SyncDomain>()
    val profiles = ProfileRepositoryDb(db) { pendingPushes += it }
    val workouts = WorkoutProgressStore(settings, db) { pendingPushes += it }
    val sleep = SleepStore(settings) { pendingPushes += it }
    val weight = WeightStore(settings) { pendingPushes += it }
    val chat = ChatHistoryStore(settings)
    val sync = NetworkSyncRepository(server.apiClient(auth), auth, owner, profiles, workouts, sleep, weight)
    val coordinator = AccountDataCoordinator(
        auth = auth,
        owner = owner,
        localData = DeviceUserData(
            profiles, AiResponseCache(settings), workouts, sleep, weight, chat,
            ExerciseCalibrationSettingsRepository(settings)
        ),
        sync = sync
    )

    /** Profile "<name>-profile", workout "<name>-workout", a weight and a private chat message. */
    suspend fun recordData(name: String) {
        profiles.upsertProfile(
            Profile(
                id = "$name-profile", age = 30, sex = Sex.FEMALE, heightCm = 170, weightKg = 65.0,
                goal = Goal.MAINTAIN, experienceLevel = 2
            )
        )
        workouts.save(WorkoutProgress(workoutId = "$name-workout", notes = "$name private note"))
        weight.logWeight("2026-09-10", 81.5)
        chat.save(listOf(ChatMessage(role = "user", content = "$name private question")))
        flushPushes()
    }

    suspend fun assertNoLocalData() {
        assertNull(profiles.getProfile())
        assertTrue(workouts.current().isEmpty())
        assertNull(weight.latestWeight())
        assertTrue(sleep.recentEntries().isEmpty())
        assertTrue(chat.load().isEmpty())
    }

    /** The app pushes from onLocalChange hooks on appScope; tests drain them deterministically. */
    suspend fun flushPushes() {
        pendingPushes.toList().also { pendingPushes.clear() }.distinct().forEach { sync.pushDomain(it) }
    }

    fun close() = driver.close()
}

/** Firebase stand-in: one current user at a time, token = "token-<uid>". */
internal class FakeAuthRepository : AuthRepository {
    override val authState = MutableStateFlow<AuthUser?>(null)
    fun signInAs(uid: String) { authState.value = AuthUser(uid, "$uid@example.test") }
    override suspend fun signUp(email: String, password: String) = error("unused")
    override suspend fun signIn(email: String, password: String) = error("unused")
    override suspend fun signInWithGoogle(idToken: String) = error("unused")
    override suspend fun signInWithApple(idToken: String, rawNonce: String) = error("unused")
    override suspend fun signOut() { authState.value = null }
    override suspend fun idToken(): String? = authState.value?.let { "token-${it.uid}" }
    override suspend fun idTokenFor(uid: String): String? =
        authState.value?.takeIf { it.uid == uid }?.let { "token-${it.uid}" }
}

/**
 * Mirrors the real /me/sync contract: blobs are stored per user, and the user is whoever the
 * bearer token belongs to — exactly like the server's Firebase auth intercept.
 */
internal class FakeSyncServer {
    private val json = Json { ignoreUnknownKeys = true }
    val blobs = mutableMapOf<String, MutableMap<String, String>>()
    var failRequests = false

    fun seed(uid: String, domain: String, payload: String) {
        blobs.getOrPut(uid) { mutableMapOf() }[domain] = payload
    }

    fun payload(uid: String, domain: String): String? = blobs[uid]?.get(domain)

    /** Same bearer rule as createHttpClient(): attach the current user's token unless the
     *  request already carries an explicit Authorization header. */
    fun apiClient(auth: AuthRepository): ApiClient {
        val engine = MockEngine { request ->
            val uid = request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer token-")
            when {
                failRequests -> respond("", HttpStatusCode.ServiceUnavailable)
                uid == null -> respond("", HttpStatusCode.Unauthorized)
                request.method == HttpMethod.Put -> {
                    val domain = request.url.encodedPath.substringAfterLast('/')
                    val body = (request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString()
                    seed(uid, domain, json.decodeFromString(SyncPushRequestDto.serializer(), body).payload)
                    respond("", HttpStatusCode.NoContent)
                }
                else -> {
                    val domains = blobs[uid].orEmpty().mapValues { SyncBlobDto(it.value, "2026-09-16T00:00:00Z") }
                    respond(
                        json.encodeToString(SyncPullResponseDto(domains)),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
            }
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(createClientPlugin("BearerAuth") {
                onRequest { request, _ ->
                    if (request.headers[HttpHeaders.Authorization] == null) {
                        auth.idToken()?.let { request.headers.append(HttpHeaders.Authorization, "Bearer $it") }
                    }
                }
            })
        }
        return ApiClient(client, "https://sync.test")
    }
}

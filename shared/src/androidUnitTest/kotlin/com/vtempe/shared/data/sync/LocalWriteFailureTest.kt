package com.vtempe.shared.data.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.russhwolf.settings.Settings
import com.vtempe.shared.data.repo.NetworkRemoteAccountRepository
import com.vtempe.shared.data.repo.WorkoutProgressStore
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.account.AccountDataCoordinator
import com.vtempe.shared.domain.account.DefaultAccountSession
import com.vtempe.shared.domain.account.LocalUserData
import com.vtempe.shared.domain.model.WorkoutProgress
import com.vtempe.shared.domain.repository.SyncDomain
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.lang.reflect.Proxy
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** VT-AUD-061 (a failed reset is reported) and VT-AUD-062 (a failed save is not shown as saved). */
class LocalWriteFailureTest {
    private val device = TestDevice()
    private val uncaught = mutableListOf<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e -> uncaught += e })

    @AfterTest
    fun tearDown() {
        scope.cancel()
        device.close()
    }

    @Test
    fun aFailedResetReachesTheCallerAndCanBeRetried() = runBlocking<Unit> {
        var failNext = true
        var cleared = 0
        val localData = object : LocalUserData {
            override suspend fun hasUserData() = true
            override suspend fun clearUserData() {
                if (failNext) { failNext = false; throw IOException("disk full") }
                cleared++
            }
        }
        val coordinator = AccountDataCoordinator(
            device.auth, device.owner, localData, device.sync, NetworkRemoteAccountRepository(device.server.apiClient(device.auth), device.auth),
            device.consent,
        )
        val session = DefaultAccountSession(coordinator, device.auth, device.owner, scope)

        assertFailsWith<IOException> { session.resetUserData() }
        assertEquals(0, cleared)
        assertTrue(uncaught.isEmpty(), "uncaught: $uncaught")

        session.resetUserData() // retry
        assertEquals(1, cleared)
    }

    @Test
    fun aFailedProgressWriteIsNotPublishedOrSynced() = runBlocking<Unit> {
        var failWrites = false
        val values = mutableMapOf<String, String>()
        val settings = Proxy.newProxyInstance(Settings::class.java.classLoader, arrayOf(Settings::class.java)) { _, method, args ->
            when (method.name) {
                "getStringOrNull" -> values[args!![0] as String]
                "putString" -> { if (failWrites) throw IllegalStateException("disk full"); values[args!![0] as String] = args[1] as String; null }
                "remove" -> { values.remove(args!![0] as String); null }
                else -> error("Unexpected Settings call: ${method.name}")
            }
        } as Settings
        JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY).use { driver ->
            AppDatabase.Schema.create(driver)
            val changes = mutableListOf<SyncDomain>()
            val store = WorkoutProgressStore(settings, AppDatabase(driver), onLocalChange = { changes += it })
            store.save(WorkoutProgress(workoutId = "w1", notes = "saved"))

            failWrites = true
            assertFailsWith<IllegalStateException> { store.save(WorkoutProgress(workoutId = "w2", notes = "lost")) }
            assertEquals(setOf("w1"), store.current().keys)
            assertEquals(listOf(SyncDomain.WORKOUT_PROGRESS), changes)

            failWrites = false
            store.save(WorkoutProgress(workoutId = "w2", notes = "retried"))
            assertEquals(setOf("w1", "w2"), store.current().keys)
        }
    }
}

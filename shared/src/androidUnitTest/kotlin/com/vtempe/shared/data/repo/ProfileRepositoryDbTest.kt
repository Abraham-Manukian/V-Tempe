package com.vtempe.shared.data.repo

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.vtempe.shared.db.AppDatabase
import com.vtempe.shared.domain.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ProfileRepositoryDbTest {
    @Test
    fun profileConstraintsSurviveRoundTripAndReplacement() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            val db = AppDatabase(driver)
            val repository = ProfileRepositoryDb(db)
            repository.upsertProfile(profile)
            assertEquals(profile, repository.getProfile())
            val updated = profile.copy(constraints = Constraints(healthNotes = listOf("test note")))
            repository.upsertProfile(updated)
            assertEquals(updated, ProfileRepositoryDb(db).getProfile())
        } finally {
            driver.close()
        }
    }

    @Test
    fun failedDetailWriteRollsBackWholeProfileAndDoesNotNotifySync() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            AppDatabase.Schema.create(driver)
            val db = AppDatabase(driver)
            var notifications = 0
            val repository = ProfileRepositoryDb(db) { notifications++ }
            repository.upsertProfile(profile)
            driver.execute(null, """
                CREATE TRIGGER reject_equipment BEFORE INSERT ON ProfileEquipment
                WHEN NEW.item = 'rejected' BEGIN SELECT RAISE(ABORT, 'test failure'); END
            """.trimIndent(), 0)
            assertFails {
                repository.upsertProfile(profile.copy(age = 35, equipment = Equipment(listOf("rejected"))))
            }
            assertEquals(profile, repository.getProfile())
            assertEquals(1, notifications)
        } finally {
            driver.close()
        }
    }

    @Test
    fun versionSevenMigrationPreservesExistingProfile() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, """
                CREATE TABLE Profile(
                  id TEXT NOT NULL PRIMARY KEY, age INTEGER NOT NULL, sex TEXT NOT NULL,
                  heightCm INTEGER NOT NULL, weightKg REAL NOT NULL, goal TEXT NOT NULL,
                  experienceLevel INTEGER NOT NULL, budgetLevel INTEGER NOT NULL DEFAULT 2,
                  trainingMode TEXT NOT NULL DEFAULT 'AUTO', coachTrainerId TEXT NOT NULL DEFAULT 'mia',
                  lifestyleActivity TEXT NOT NULL DEFAULT 'SEDENTARY', trainingFocus TEXT NOT NULL DEFAULT 'GENERAL',
                  sessionDurationMins INTEGER NOT NULL DEFAULT 60, splitPreference TEXT NOT NULL DEFAULT 'AUTO'
                )
            """.trimIndent(), 0)
            driver.execute(null, """
                INSERT INTO Profile(id, age, sex, heightCm, weightKg, goal, experienceLevel)
                VALUES ('existing', 28, 'MALE', 178, 78.0, 'MAINTAIN', 3)
            """.trimIndent(), 0)
            AppDatabase.Schema.migrate(driver, 7, 8)
            val row = assertNotNull(AppDatabase(driver).profileQueries.selectProfile().executeAsOneOrNull())
            assertEquals("existing", row.id)
            assertEquals(78.0, row.weightKg)
            assertEquals("{}", row.constraintsJson)
        } finally {
            driver.close()
        }
    }

    private val profile = Profile(
        id = "test-profile", age = 28, sex = Sex.MALE, heightCm = 178, weightKg = 78.0,
        goal = Goal.MAINTAIN, experienceLevel = 3,
        constraints = Constraints(injuries = listOf("test knee", "test shoulder")),
        equipment = Equipment(listOf("dumbbells")), dietaryPreferences = listOf("test preference"),
        allergies = listOf("test allergy"), weeklySchedule = mapOf("Mon" to true)
    )
}

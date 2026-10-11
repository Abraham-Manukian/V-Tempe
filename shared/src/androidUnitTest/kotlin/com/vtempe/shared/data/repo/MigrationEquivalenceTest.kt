package com.vtempe.shared.data.repo

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.vtempe.shared.db.AppDatabase
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Migration gate (VT-AUD-084). SQLDelight's own verifier is disabled because it needs native SQLite
 * and schema snapshots; this test runs on every OS with the JDBC driver. A database at schema
 * version 1 (a synthetic fixture taken from the .sq files before the first migration) holding data
 * is migrated to the current version and must end up with exactly the schema a fresh install
 * creates — same tables, columns, types, NOT NULL, defaults, keys, indexes and foreign keys — with
 * the old rows still readable. A new .sqm that drifts from the .sq files fails here.
 */
class MigrationEquivalenceTest {

    private fun v1Statements(): List<String> {
        val sql = checkNotNull(javaClass.classLoader.getResource("migrations/schema-v1.sql")).readText()
        return sql.lines().filterNot { it.trimStart().startsWith("--") }.joinToString("\n")
            .replace(Regex("--[^\n]*"), "")
            .split(';').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun SqlDriver.strings(sql: String, column: Int = 0): List<String> =
        executeQuery(null, sql, { c ->
            val out = mutableListOf<String>()
            while (c.next().value) out += c.getString(column).orEmpty()
            QueryResult.Value(out)
        }, 0).value

    private fun SqlDriver.rows(sql: String, columns: Int): Set<List<String?>> =
        executeQuery(null, sql, { c ->
            val out = mutableSetOf<List<String?>>()
            while (c.next().value) out += (0 until columns).map { c.getString(it) }
            QueryResult.Value(out)
        }, 0).value

    /** Everything about the schema that matters, independent of column order. */
    private fun SqlDriver.describe(): Map<String, Any> {
        val tables = strings("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")
        val shape = tables.associateWith { table ->
            mapOf(
                // cid, name, type, notnull, dflt_value, pk — cid dropped: ALTER TABLE appends columns.
                "columns" to rows("PRAGMA table_info('$table')", 6).map { it.drop(1) }.toSet(),
                "foreignKeys" to rows("PRAGMA foreign_key_list('$table')", 8).map { it.drop(2) }.toSet(),
                "indexes" to rows("PRAGMA index_list('$table')", 5).filterNot { it[1].orEmpty().startsWith("sqlite_autoindex") }
                    .map { listOf(it[1], it[2]) }.toSet(),
            )
        }
        return mapOf("tables" to tables, "shape" to shape)
    }

    @Test
    fun migratingFromVersionOneGivesTheFreshSchemaAndKeepsTheData() = runBlocking {
        val migrated = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            v1Statements().forEach { migrated.execute(null, it, 0) }
            // A version-1 user: profile, schedule, a workout with a set, a meal.
            migrated.execute(null, "INSERT INTO Profile(id, age, sex, heightCm, weightKg, goal, experienceLevel, budgetLevel) VALUES ('p1', 34, 'FEMALE', 168, 61.5, 'LOSE_FAT', 2, 2)", 0)
            migrated.execute(null, "INSERT INTO ProfileSchedule(profileId, day, active) VALUES ('p1', 'Mon', 1)", 0)
            migrated.execute(null, "INSERT INTO Workout(id, weekIndex, date) VALUES ('w1', 0, '2026-04-06')", 0)
            migrated.execute(null, "INSERT INTO WorkoutSet(workoutId, exerciseId, reps, weightKg, rpe) VALUES ('w1', 'squat', 8, 40.0, 7.5)", 0)
            migrated.execute(null, "INSERT INTO Meal(id, name, kcal, protein, fat, carbs) VALUES ('m1', 'Oats', 350, 12, 6, 60)", 0)

            AppDatabase.Schema.migrate(migrated, 1, AppDatabase.Schema.version)
            AppDatabase.Schema.create(fresh)

            assertEquals(fresh.describe(), migrated.describe())

            val profile = assertNotNull(ProfileRepositoryDb(AppDatabase(migrated)).getProfile())
            assertEquals(61.5, profile.weightKg)
            assertEquals(mapOf("Mon" to true), profile.weeklySchedule)
            assertEquals(listOf("squat"), migrated.strings("SELECT exerciseId FROM WorkoutSet WHERE workoutId = 'w1'"))
            assertEquals(listOf("3"), migrated.strings("SELECT sets FROM WorkoutSet"), "the migration default for older sets")
            assertEquals(listOf("Oats"), migrated.strings("SELECT name FROM Meal"))
        } finally {
            migrated.close()
            fresh.close()
        }
    }
}

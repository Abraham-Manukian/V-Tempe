package com.vtempe.shared.domain.training

import com.vtempe.shared.data.training.OfflineExercisePool
import com.vtempe.shared.domain.exercise.ExerciseLibrary
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Client side of the training contracts (see server TrainingContractTest). */
class TrainingContractTest {

    private fun rows(name: String) = File("../testdata/$name").readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }

    @Test
    fun offlinePoolMatchesTheContractFile() {
        val expected = rows("offline-training-pool.tsv").map { r ->
            listOf(r[0], r[1], r[2].split(';').filter { it.isNotEmpty() }.sorted().joinToString(";"),
                r[3].split(';').filter { it.isNotEmpty() }.sorted().joinToString(";"), r[4], r[5])
        }
        val actual = OfflineExercisePool.map { e ->
            listOf(e.id, e.primary.name, e.secondary.map { it.name }.sorted().joinToString(";"),
                e.equipmentAnyOf.sorted().joinToString(";"), e.difficulty.toString(), e.priority.toString())
        }
        assertEquals(expected, actual)
        // Every pool exercise must be renderable on the device.
        assertTrue(OfflineExercisePool.all { e -> ExerciseLibrary.all().any { it.supports(e.id) } })
    }

    @Test
    fun clientInjuryRulesAgreeWithTheContractCases() {
        val pool = OfflineExercisePool.associateBy { it.id }
        val mismatches = rows("injury-restriction-cases.tsv").mapNotNull { (injury, id, expected) ->
            val exercise = pool.getValue(id)
            val restrictions = ExerciseRestrictions.forInjuries(listOf(injury).filter { it.isNotBlank() })
            val actual = if (restrictions.allows(id, exercise.primary, exercise.secondary)) "allowed" else "banned"
            "'$injury' / $id: expected $expected, got $actual".takeIf { actual != expected }
        }
        assertEquals(emptyList(), mismatches)
    }
}

package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.service.split.ExerciseRestrictions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

/** Server side of the training contracts shared with the client offline planner: the offline
 *  pool must describe exercises exactly as the catalog does, and injury rules must agree. */
class TrainingContractTest {

    private val catalog = BuiltInExerciseCatalog()

    private fun rows(name: String) = File("../testdata/$name").readLines(Charsets.UTF_8)
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { it.split('\t') }

    @Test
    fun `offline pool matches the server catalog`() {
        val mismatches = rows("offline-training-pool.tsv").mapNotNull { (id, primary, secondary, equipment, difficulty, priority) ->
            val item = catalog.findByIdOrAlias(id) ?: return@mapNotNull "$id: not in catalog"
            val expected = listOf(
                item.primaryPattern.name,
                item.secondaryPatterns.map { it.name }.sorted().joinToString(";"),
                item.requiredEquipment.sorted().joinToString(";"),
                item.difficulty.toString(),
                item.priority.toString(),
            )
            val actual = listOf(primary, secondary.split(';').filter { it.isNotEmpty() }.sorted().joinToString(";"),
                equipment.split(';').filter { it.isNotEmpty() }.sorted().joinToString(";"), difficulty, priority)
            "$id: catalog $expected, pool $actual".takeIf { expected != actual }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    @Test
    fun `server injury rules agree with the shared contract cases`() {
        val mismatches = rows("injury-restriction-cases.tsv").mapNotNull { (injury, id, expected) ->
            val injuries = listOf(injury).filter { it.isNotBlank() }
            val actual = if (ExerciseRestrictions.forInjuries(injuries).allows(id, catalog)) "allowed" else "banned"
            "'$injury' / $id: expected $expected, got $actual".takeIf { actual != expected }
        }
        assertEquals(emptyList<String>(), mismatches)
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}

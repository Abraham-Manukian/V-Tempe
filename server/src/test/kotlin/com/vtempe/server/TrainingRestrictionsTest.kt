package com.vtempe.server

import com.vtempe.server.features.ai.data.catalog.BuiltInExerciseCatalog
import com.vtempe.server.features.ai.data.resolver.DefaultTrainingPlanResolver
import com.vtempe.server.features.ai.data.service.CoachEditApplicator
import com.vtempe.server.features.ai.data.service.NoSafeTrainingPlanException
import com.vtempe.server.features.ai.data.service.NormalizationMode
import com.vtempe.server.features.ai.data.service.fallbackTraining
import com.vtempe.server.features.ai.data.service.normalizeTrainingPlan
import com.vtempe.server.features.ai.data.service.split.ExerciseRestrictions
import com.vtempe.server.features.ai.data.service.validateTrainingPlan
import com.vtempe.server.features.ai.domain.model.ExerciseCatalogItem
import com.vtempe.server.features.ai.domain.model.MovementPattern
import com.vtempe.server.features.ai.domain.model.TrainingMode
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.shared.dto.chat.CoachEditOp
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingRequest
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** VT-AUD-014 (one set of injury rules on every path) and VT-AUD-015 (load follows the exercise). */
class TrainingRestrictionsTest {

    private val catalog = BuiltInExerciseCatalog()
    private val resolver = DefaultTrainingPlanResolver(catalog)

    private fun profile(
        injuries: List<String> = emptyList(),
        equipment: List<String> = emptyList(),
        trainingMode: String = "AUTO",
        days: List<String> = emptyList(),
        level: Int = 2,
    ) = AiProfile(
        age = 30, sex = "MALE", heightCm = 180, weightKg = 80.0, goal = "MAINTAIN", experienceLevel = level,
        equipment = equipment, injuries = injuries, trainingMode = trainingMode,
        weeklySchedule = days.associateWith { true },
    )

    private fun plan(vararg sets: AiSet, workouts: Int = 1) = AiTrainingResponse(
        weekIndex = 0,
        workouts = List(workouts) { AiWorkout(id = "w$it", date = "2026-07-06", sets = sets.toList()) },
    )

    private fun AiTrainingResponse.exerciseIds() = workouts.flatMap { w -> w.sets.map { it.exerciseId } }

    private fun assertAllAllowed(normalized: AiTrainingResponse, injuries: List<String>) {
        val restrictions = ExerciseRestrictions.forInjuries(injuries)
        normalized.exerciseIds().forEach { id ->
            assertTrue(restrictions.allows(id, catalog), "'$id' is banned for $injuries")
            assertTrue(id in catalog.supportedExerciseIds(), "'$id' is not in the catalog")
        }
    }

    // ── VT-AUD-014 ─────────────────────────────────────────────────────────────

    @Test
    fun `empty-result fallback never reintroduces a pattern banned for the injury`() {
        // Audit scenario: no equipment, no schedule, knee pain, unknown exercise id.
        val knee = profile(injuries = listOf("knee pain"))
        val normalized = normalizeTrainingPlan(plan(AiSet("made_up_exercise", 8, null, 7.0)), knee)

        assertAllAllowed(normalized, knee.injuries)
        val patterns = normalized.exerciseIds().map { catalog.findByIdOrAlias(it)!!.primaryPattern }
        assertTrue(MovementPattern.KNEE_DOMINANT !in patterns && MovementPattern.SINGLE_LEG !in patterns, patterns.toString())
        assertNull(validateTrainingPlan(normalized, catalog, knee.injuries))
    }

    @Test
    fun `validator rejects an exercise of a banned pattern even when its id is not on the ban list`() {
        val plan = plan(AiSet("bodyweight_squat", 10, null, 7.0))
        val error = validateTrainingPlan(plan, catalog, listOf("knee pain"))
        assertNotNull(error)
        assertTrue(error!!.contains("contraindicated"), error)
        assertNull(validateTrainingPlan(plan, catalog, emptyList()))
    }

    @Test
    fun `an empty catalog yields no workout instead of an invented id`() {
        val empty = object : ExerciseCatalog {
            override fun all() = emptyList<ExerciseCatalogItem>()
            override fun supportedExerciseIds() = emptySet<String>()
            override fun findByIdOrAlias(rawToken: String): ExerciseCatalogItem? = null
            override fun availablePatterns(mode: TrainingMode, equipment: Set<String>) = emptyList<MovementPattern>()
            override fun candidatesFor(pattern: MovementPattern, mode: TrainingMode, equipment: Set<String>) =
                emptyList<ExerciseCatalogItem>()
        }
        val normalized = normalizeTrainingPlan(
            plan(AiSet("made_up_exercise", 8, null, 7.0)), profile(), DefaultTrainingPlanResolver(empty), catalog = empty
        )
        assertTrue(normalized.workouts.isEmpty(), normalized.toString())
        assertEquals("workouts array must contain at least one workout", validateTrainingPlan(normalized, empty))
    }

    @Test
    fun `generated skeleton honours every injury rule for every equipment setup`() {
        val injurySets = listOf(
            listOf("knee pain"), listOf("lower back hernia"), listOf("shoulder impingement"), listOf("wrist"),
            listOf("tennis elbow"), listOf("neck"), listOf("ankle sprain"), listOf("hip"),
            listOf("нельзя брусья"), listOf("болит колено", "грыжа поясницы"),
        )
        val setups = listOf(
            Triple(emptyList<String>(), "HOME", "home, nothing"),
            Triple(listOf("dumbbells", "bench", "pullup_bar"), "HOME", "home, gear"),
            Triple(listOf("barbell", "dumbbells", "bench"), "GYM", "gym"),
        )
        val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri")
        for (injuries in injurySets) for ((equipment, mode, label) in setups) {
            val p = profile(injuries = injuries, equipment = equipment, trainingMode = mode, days = days, level = 3)
            val llm = plan(AiSet("squat", 8, 100.0, 8.0), AiSet("deadlift", 5, 120.0, 8.0), workouts = days.size)
            val normalized = normalizeTrainingPlan(llm, p)
            assertTrue(normalized.workouts.isNotEmpty(), "$injuries / $label")
            assertAllAllowed(normalized, injuries)
            assertNull(validateTrainingPlan(normalized, catalog, injuries), "$injuries / $label")
        }
    }

    @Test
    fun `server fallback plan obeys the restrictions or refuses`() {
        val everything = listOf("knee", "back", "shoulder", "wrist", "elbow", "neck", "ankle", "hip")
        for (injuries in listOf(listOf("knee pain"), listOf("back", "wrist"), everything)) {
            val p = profile(injuries = injuries, days = listOf("Mon", "Wed", "Fri"))
            val result = runCatching { fallbackTraining(AiTrainingRequest(p, weekIndex = 0)) }
            result.onSuccess { assertAllAllowed(it, injuries); assertTrue(it.workouts.isNotEmpty()) }
            result.onFailure { assertTrue(it is NoSafeTrainingPlanException, it.toString()) }
        }
    }

    // ── VT-AUD-015 ─────────────────────────────────────────────────────────────

    private val gym = profile(equipment = listOf("barbell", "dumbbells", "bench"), trainingMode = "GYM", days = listOf("Mon"))

    private fun skeletonIds(): List<String> =
        normalizeTrainingPlan(plan(AiSet("x", 8, null, 7.0)), gym).workouts.first().sets.map { it.exerciseId }

    @Test
    fun `weight of a different exercise never lands on the skeleton slot`() {
        val ids = skeletonIds()
        // Audit scenario: the model wrote a load for another exercise in the slot of a loaded one.
        val slot = ids.indexOfFirst { catalog.findByIdOrAlias(it)?.isBodyweightOnly == false }
        assertTrue(slot >= 0, "skeleton has no loaded exercise: $ids")
        val stranger = listOf("bench", "deadlift", "squat").first { it !in ids }
        val sets = ids.mapIndexed { i, id -> if (i == slot) AiSet(stranger, 5, 123.0, 7.0) else AiSet(id, 8, null, 7.0) }
        val normalized = normalizeTrainingPlan(plan(*sets.toTypedArray()), gym).workouts.first().sets
        assertEquals(ids[slot], normalized[slot].exerciseId)
        assertNull(normalized[slot].weightKg)

        // Control: the same exercise keeps its load.
        val own = ids.mapIndexed { i, id -> AiSet(id, 8, if (i == slot) 123.0 else null, 7.0) }
        assertEquals(123.0, normalizeTrainingPlan(plan(*own.toTypedArray()), gym).workouts.first().sets[slot].weightKg)
    }

    @Test
    fun `weight follows its exercise across reordered slots and aliases`() {
        val ids = skeletonIds()
        val loads = ids.mapIndexed { i, id -> id to 20.0 + i * 10 }.toMap()
        // The model lists the same exercises in reverse order.
        val reversed = ids.reversed().map { AiSet(it, 8, loads.getValue(it), 7.0) }.toTypedArray()
        val normalized = normalizeTrainingPlan(plan(*reversed), gym).workouts.first().sets
        normalized.forEach { set ->
            if (catalog.findByIdOrAlias(set.exerciseId)?.isBodyweightOnly == true) assertNull(set.weightKg)
            else assertEquals(loads.getValue(set.exerciseId), set.weightKg, set.exerciseId)
        }

        val first = ids.first()
        val alias = catalog.findByIdOrAlias(first)!!.aliases.firstOrNull() ?: return
        val viaAlias = normalizeTrainingPlan(plan(AiSet(alias, 8, 55.0, 7.0)), gym).workouts.first().sets.first()
        if (catalog.findByIdOrAlias(first)!!.isBodyweightOnly) assertNull(viaAlias.weightKg) else assertEquals(55.0, viaAlias.weightKg)
    }

    @Test
    fun `non-finite or negative loads become unknown`() {
        val first = skeletonIds().first()
        for (bad in listOf(-5.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            val set = normalizeTrainingPlan(plan(AiSet(first, 8, bad, 7.0)), gym).workouts.first().sets.first()
            assertNull(set.weightKg, "load $bad")
        }
    }

    // ── Chat edits (same classes of defect) ──────────────────────────────────

    private val applicator = CoachEditApplicator(catalog, resolver)
    private val current = AiTrainingResponse(
        weekIndex = 0,
        workouts = listOf(AiWorkout(id = "w1", date = "2026-07-06", sets = listOf(
            AiSet("bench", 8, 60.0, 7.0), AiSet("row", 8, 50.0, 7.0)
        )))
    )

    @Test
    fun `chat cannot swap or add an exercise banned for the injury`() {
        val knee = profile(injuries = listOf("knee pain"), equipment = listOf("barbell"), trainingMode = "GYM")
        for (op in listOf(
            CoachEditOp(op = "swap_exercise", exerciseId = "row", newExerciseId = "bodyweight_squat"),
            CoachEditOp(op = "add_exercise", workoutId = "w1", newExerciseId = "lunge"),
        )) {
            val result = applicator.apply(listOf(op), current, null, knee)
            assertFalse(result.trainingChanged, op.op)
            assertTrue(result.rejections.single().contains("contraindicated"), result.rejections.toString())
        }
    }

    @Test
    fun `chat swap does not carry the old exercise's weight`() {
        val result = applicator.apply(
            listOf(CoachEditOp(op = "swap_exercise", exerciseId = "bench", newExerciseId = "deadlift")),
            current, null, gym
        )
        val swapped = result.trainingPlan!!.workouts.first().sets.first()
        assertEquals("deadlift", swapped.exerciseId)
        assertNull(swapped.weightKg)
    }

    @Test
    fun `edit normalization drops exercises of banned patterns`() {
        val knee = profile(injuries = listOf("knee pain"))
        val edited = normalizeTrainingPlan(
            plan(AiSet("bodyweight_squat", 10, null, 7.0), AiSet("pushup", 10, null, 7.0)),
            knee, resolver, mode = NormalizationMode.EDIT
        )
        assertEquals(listOf("pushup"), edited.exerciseIds())
    }
}

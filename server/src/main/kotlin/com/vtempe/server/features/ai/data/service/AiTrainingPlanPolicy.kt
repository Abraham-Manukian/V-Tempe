package com.vtempe.server.features.ai.data.service

import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

private const val MaxWorkoutsPerPlan = 5
private const val MaxSetsPerWorkout = 6

private val allowedExerciseIds = setOf(
    "squat",
    "bench",
    "deadlift",
    "ohp",
    "row",
    "pullup",
    "lunge",
    "dip",
    "pushup",
    "curl",
    "tricep_extension",
    "plank",
    "hip_thrust",
    "leg_press",
    "run",
    "bike",
    "yoga"
)

private val exerciseAliasMap = mapOf(
    "back_squat" to "squat",
    "bench_press" to "bench",
    "bent_over_row" to "row",
    "barbell_row" to "row",
    "pull_up" to "pullup",
    "pullups" to "pullup",
    "walking_lunge" to "lunge",
    "parallel_bar_dip" to "dip",
    "parallel_bar_dips" to "dip",
    "push_up" to "pushup",
    "push_ups" to "pushup",
    "bicep_curl" to "curl",
    "biceps_curl" to "curl",
    "biceps_curls" to "curl",
    "triceps_extension" to "tricep_extension",
    "triceps_extensions" to "tricep_extension",
    "hipthrust" to "hip_thrust",
    "hip_thrusts" to "hip_thrust",
    "plank_hold" to "plank",
    "legpress" to "leg_press",
    "cycling" to "bike"
)

internal fun normalizeTrainingPlan(plan: AiTrainingResponse): AiTrainingResponse {
    val weekStart = expectedWeekStart(plan.weekIndex)
    val usedWorkoutIds = mutableSetOf<String>()
    val workouts = plan.workouts
        .take(MaxWorkoutsPerPlan)
        .mapIndexed { index, workout ->
            val safeDate = normalizeWorkoutDate(workout.date, weekStart, index)
            val rawId = sanitizeText(workout.id).ifEmpty { "w_${plan.weekIndex}_$index" }
            val safeId = uniqueWorkoutId(rawId, plan.weekIndex, index, usedWorkoutIds)

            val normalizedSets = workout.sets
                .mapNotNull { set ->
                    val canonical = canonicalExerciseIdOrNull(set.exerciseId) ?: return@mapNotNull null
                    val reps = set.reps.coerceAtLeast(1)
                    val weight = set.weightKg?.takeIf { it >= 0.0 }
                    val rpe = set.rpe?.takeIf { it > 0.0 }
                    AiSet(
                        exerciseId = canonical,
                        reps = reps,
                        weightKg = weight,
                        rpe = rpe
                    )
                }
                .distinctBy { listOf(it.exerciseId, it.reps, it.weightKg, it.rpe) }
                .take(MaxSetsPerWorkout)

            val safeSets = if (normalizedSets.isEmpty()) {
                listOf(AiSet("squat", reps = 8, weightKg = null, rpe = 7.0))
            } else {
                normalizedSets
            }

            AiWorkout(
                id = safeId,
                date = safeDate,
                sets = safeSets
            )
        }

    return plan.copy(workouts = workouts)
}

internal fun validateTrainingPlan(plan: AiTrainingResponse): String? {
    if (plan.workouts.isEmpty()) return "workouts array must contain at least one workout"
    if (plan.workouts.any { it.sets.isEmpty() }) return "each workout must include at least one set"

    val workoutIds = mutableSetOf<String>()
    plan.workouts.forEachIndexed { workoutIndex, workout ->
        val id = normalizeExerciseToken(workout.id)
        if (id.isBlank()) return "workout[$workoutIndex].id must not be blank"
        if (!workoutIds.add(id)) return "workout ids must be unique"

        if (workout.sets.size > MaxSetsPerWorkout * 2) {
            return "workout[$workoutIndex] has too many sets (${workout.sets.size})"
        }
        val seenSets = mutableSetOf<String>()
        workout.sets.forEachIndexed { setIndex, set ->
            val canonicalExercise = canonicalExerciseIdOrNull(set.exerciseId)
                ?: return "workout[$workoutIndex].sets[$setIndex].exerciseId is not supported"
            if (set.reps <= 0) return "workout[$workoutIndex].sets[$setIndex].reps must be positive"
            val fingerprint = "$canonicalExercise|${set.reps}|${set.weightKg ?: "bw"}|${set.rpe ?: "-"}"
            if (!seenSets.add(fingerprint)) {
                return "workout[$workoutIndex] contains duplicate sets"
            }
        }
    }
    return null
}

private fun expectedWeekStart(weekIndex: Int): LocalDate {
    val today = LocalDate.now(ZoneOffset.UTC)
    val nextMonday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
    return nextMonday.plusWeeks(weekIndex.toLong())
}

private fun normalizeWorkoutDate(raw: String, weekStart: LocalDate, index: Int): String {
    val parsed = runCatching { LocalDate.parse(raw) }.getOrElse { weekStart.plusDays(index.toLong()) }
    val weekEnd = weekStart.plusDays(6)
    val safeDate = if (parsed.isBefore(weekStart) || parsed.isAfter(weekEnd)) {
        weekStart.plusDays(index.toLong().coerceAtMost(6))
    } else {
        parsed
    }
    return safeDate.toString()
}

private fun uniqueWorkoutId(
    rawId: String,
    weekIndex: Int,
    index: Int,
    used: MutableSet<String>
): String {
    val normalized = normalizeExerciseToken(rawId).ifEmpty { "w_${weekIndex}_$index" }
    var candidate = normalized
    var suffix = 1
    while (!used.add(candidate)) {
        candidate = "${normalized}_$suffix"
        suffix += 1
    }
    return candidate
}

private fun canonicalExerciseIdOrNull(raw: String): String? {
    val trimmed = sanitizeText(raw)
    if (trimmed.isEmpty()) return null

    val explicitId = Regex("""\(([^()]+)\)\s*$""")
        .find(trimmed)
        ?.groupValues
        ?.getOrNull(1)
        ?.let(::normalizeExerciseToken)
        ?.let { token -> if (token in allowedExerciseIds) token else exerciseAliasMap[token] }
    if (explicitId != null) return explicitId

    val token = normalizeExerciseToken(trimmed)
    return when {
        token in allowedExerciseIds -> token
        else -> exerciseAliasMap[token]
    }
}

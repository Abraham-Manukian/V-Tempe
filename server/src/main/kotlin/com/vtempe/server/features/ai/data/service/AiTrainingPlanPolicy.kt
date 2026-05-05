package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

private const val MaxWorkoutsPerPlan = 5
private const val MaxSetsPerWorkout = 6

internal fun normalizeTrainingPlan(
    plan: AiTrainingResponse,
    profile: AiProfile? = null,
    trainingPlanResolver: TrainingPlanResolver = builtInTrainingPlanResolver
): AiTrainingResponse {
    val weekStart = expectedWeekStart(plan.weekIndex)
    val usedWorkoutIds = mutableSetOf<String>()
    val workouts = plan.workouts
        .take(MaxWorkoutsPerPlan)
        .mapIndexed { index, workout ->
            val safeDate = normalizeWorkoutDate(workout.date, weekStart, index)
            val rawId = sanitizeText(workout.id).ifEmpty { "w_${plan.weekIndex}_$index" }
            val safeId = uniqueWorkoutId(rawId, plan.weekIndex, index, usedWorkoutIds)
            val usedExerciseIds = mutableSetOf<String>()

            val normalizedSets = workout.sets
                .mapIndexedNotNull { setIndex, set ->
                    val canonical = trainingPlanResolver.resolveExerciseId(
                        rawToken = set.exerciseId,
                        trainingModeRaw = profile?.trainingMode,
                        equipment = profile?.equipment.orEmpty(),
                        usedExerciseIds = usedExerciseIds,
                        rotationSeed = (index * 31) + setIndex
                    ) ?: return@mapIndexedNotNull null
                    usedExerciseIds += canonical
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
                .distinctBy { it.exerciseId }
                .take(MaxSetsPerWorkout)

            val safeSets = if (normalizedSets.isEmpty()) {
                val fallbackExerciseId = trainingPlanResolver.resolveExerciseId(
                    rawToken = "pattern:knee_dominant",
                    trainingModeRaw = profile?.trainingMode,
                    equipment = profile?.equipment.orEmpty(),
                    rotationSeed = index
                ) ?: "lunge"
                listOf(AiSet(fallbackExerciseId, reps = 8, weightKg = null, rpe = 7.0))
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

internal fun validateTrainingPlan(
    plan: AiTrainingResponse,
    exerciseCatalog: ExerciseCatalog = builtInExerciseCatalog
): String? {
    if (plan.workouts.isEmpty()) return "workouts array must contain at least one workout"
    if (plan.workouts.any { it.sets.isEmpty() }) return "each workout must include at least one set"

    val allowedExerciseIds = exerciseCatalog.supportedExerciseIds()
    val workoutIds = mutableSetOf<String>()
    plan.workouts.forEachIndexed { workoutIndex, workout ->
        val id = normalizeExerciseToken(workout.id)
        if (id.isBlank()) return "workout[$workoutIndex].id must not be blank"
        if (!workoutIds.add(id)) return "workout ids must be unique"

        if (workout.sets.size > MaxSetsPerWorkout * 2) {
            return "workout[$workoutIndex] has too many sets (${workout.sets.size})"
        }
        val seenExercises = mutableSetOf<String>()
        val seenSets = mutableSetOf<String>()
        workout.sets.forEachIndexed { setIndex, set ->
            val canonicalExercise = normalizeExerciseToken(set.exerciseId)
            if (canonicalExercise !in allowedExerciseIds) {
                return "workout[$workoutIndex].sets[$setIndex].exerciseId is not supported"
            }
            if (!seenExercises.add(canonicalExercise)) {
                return "workout[$workoutIndex] repeats exercise '$canonicalExercise'; use each exercise only once per workout"
            }
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

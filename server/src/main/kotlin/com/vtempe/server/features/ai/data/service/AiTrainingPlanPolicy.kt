package com.vtempe.server.features.ai.data.service

import com.vtempe.server.features.ai.data.service.split.TrainingSplitPlanner
import com.vtempe.server.features.ai.domain.port.ExerciseCatalog
import com.vtempe.server.features.ai.domain.port.TrainingPlanResolver
import com.vtempe.server.shared.dto.profile.AiProfile
import com.vtempe.server.shared.dto.training.AiSet
import com.vtempe.server.shared.dto.training.AiTrainingResponse
import com.vtempe.server.shared.dto.training.AiWorkout
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

private const val MaxWorkoutsPerPlan = 5
private const val MaxSetsPerWorkout = 6

// Exercises measured in seconds — AI should put seconds in the `reps` field (e.g. reps=30 = 30s hold).
internal val durationSecondsExerciseIds = setOf(
    "plank", "side_plank", "wall_sit", "l_sit", "hollow_body", "hollow_hold",
    "bike", "stationary_bike", "cycling", "mountain_climber", "mountain_climbers"
)

// Exercises measured in minutes — AI should put minutes in the `reps` field.
internal val durationMinutesExerciseIds = setOf(
    "run", "running", "jog", "jogging", "treadmill"
)

// Exercises where external weight should always be null (no barbell/dumbbell added)
// Advanced users with a weight belt are the exception, but we keep weight null by default.
private val bodweightOnlyExerciseIds = setOf(
    "pullup", "chin_up", "wide_pullup", "assisted_pullup", "muscle_up",
    "pushup", "diamond_pushup", "wide_pushup", "decline_pushup", "incline_pushup", "pike_pushup",
    "dip", "inverted_row", "handstand_pushup",
    "plank", "side_plank", "mountain_climber", "burpee", "jumping_jack", "jump_squat",
    "toes_to_bar", "hanging_knee_raise", "hanging_leg_raise",
    "l_sit", "hollow_body", "hollow_hold",
    "wall_sit", "lunge", "reverse_lunge", "walking_lunge", "split_squat",
    "sumo_squat", "glute_bridge", "glute_bridge_hold", "nordic_curl", "step_up",
    "bear_crawl", "crab_walk", "inchworm"
)

internal fun normalizeTrainingPlan(
    plan: AiTrainingResponse,
    profile: AiProfile? = null,
    trainingPlanResolver: TrainingPlanResolver = builtInTrainingPlanResolver,
    /**
     * When set, overrides whatever weekIndex the LLM returned.
     * LLMs sometimes output the wrong weekIndex (e.g. 1 instead of 0),
     * which causes the client to store workouts under the wrong week and
     * never find them via observeWorkoutsByWeek(requestedWeekIndex).
     */
    enforcedWeekIndex: Int? = null
): AiTrainingResponse {
    val resolvedWeekIndex = enforcedWeekIndex ?: plan.weekIndex
    val weekStart = expectedWeekStart(resolvedWeekIndex)
    val usedWorkoutIds = mutableSetOf<String>()

    // Pre-resolve ALL exercises from the skeleton — AI only provides reps/weight/rpe.
    // This guarantees correct exercises per slot regardless of what AI returned.
    val skeletonData = profile?.let {
        computeSkeletonData(it, resolvedWeekIndex, trainingPlanResolver)
    }
    val skeletonLabels = skeletonData?.map { it.first } ?: emptyList()
    val skeletonExercises = skeletonData?.map { it.second } ?: emptyList()

    val workouts = plan.workouts
        .take(MaxWorkoutsPerPlan)
        .mapIndexed { index, workout ->
            val safeDate = normalizeWorkoutDate(workout.date, weekStart, index)
            val rawId = sanitizeText(workout.id).ifEmpty { "w_${resolvedWeekIndex}_$index" }
            val safeId = uniqueWorkoutId(rawId, resolvedWeekIndex, index, usedWorkoutIds)

            val preResolved = skeletonExercises.getOrNull(index)
            val preResolvedSets = skeletonData?.getOrNull(index)?.third
            val normalizedSets = if (preResolved != null) {
                // Skeleton-driven: exercise IDs are authoritative.
                // Weight is kept only when AI chose the same exercise — otherwise null,
                // because 70kg deadlift ≠ 70kg barbell row.
                preResolved.mapIndexed { slotIndex, exerciseId ->
                    val aiSet = workout.sets.getOrNull(slotIndex)
                    val aiExerciseMatches = aiSet != null &&
                        normalizeExerciseToken(aiSet.exerciseId) == exerciseId
                    val reps = aiSet?.reps?.coerceAtLeast(1) ?: 8
                    val weight = when {
                        exerciseId in bodweightOnlyExerciseIds -> null
                        // In skeleton path we override the exercise ID ourselves, so AI's weight
                        // is always the intended slot weight — use it regardless of exercise match.
                        else -> aiSet?.weightKg?.takeIf { it >= 0.0 }
                    }
                    val rpe = aiSet?.rpe?.takeIf { it > 0.0 } ?: 7.5
                    val setsCount = preResolvedSets?.getOrNull(slotIndex) ?: 3
                    AiSet(exerciseId = exerciseId, reps = reps, weightKg = weight, rpe = rpe, sets = setsCount)
                }.take(MaxSetsPerWorkout)
            } else {
                // No skeleton available — fall back to resolver-based flow.
                val usedExerciseIds = mutableSetOf<String>()
                workout.sets
                    .mapIndexedNotNull { setIndex, set ->
                        val canonical = trainingPlanResolver.resolveExerciseId(
                            rawToken = set.exerciseId,
                            trainingModeRaw = profile?.trainingMode,
                            userExperienceLevel = profile?.experienceLevel ?: 3,
                            equipment = profile?.equipment.orEmpty(),
                            usedExerciseIds = usedExerciseIds,
                            rotationSeed = (index * 31) + setIndex
                        ) ?: return@mapIndexedNotNull null
                        usedExerciseIds += canonical
                        val reps = set.reps.coerceAtLeast(1)
                        val weight = if (canonical in bodweightOnlyExerciseIds) null
                                     else set.weightKg?.takeIf { it >= 0.0 }
                        val rpe = set.rpe?.takeIf { it > 0.0 }
                        AiSet(exerciseId = canonical, reps = reps, weightKg = weight, rpe = rpe)
                    }
                    .distinctBy { it.exerciseId }
                    .take(MaxSetsPerWorkout)
            }

            val safeSets = normalizedSets.ifEmpty {
                val fallback = trainingPlanResolver.resolveExerciseId(
                    rawToken = "pattern:knee_dominant",
                    trainingModeRaw = profile?.trainingMode,
                    userExperienceLevel = profile?.experienceLevel ?: 3,
                    equipment = profile?.equipment.orEmpty(),
                    rotationSeed = index
                ) ?: "lunge"
                listOf(AiSet(fallback, reps = 8, weightKg = null, rpe = 7.0))
            }

            val label = skeletonLabels.getOrNull(index) ?: workout.label.trimDayPrefix()

            AiWorkout(id = safeId, label = label, date = safeDate, sets = safeSets)
        }

    return plan.copy(weekIndex = resolvedWeekIndex, workouts = workouts)
}

/**
 * Builds skeleton data: label + pre-resolved exercise IDs + sets count per slot.
 * Returns triples of (sessionLabel, listOfExerciseIds, listOfSetsCounts).
 * Exercise IDs are authoritative — AI output is used only for reps/weight/rpe.
 */
private fun computeSkeletonData(
    profile: AiProfile,
    weekIndex: Int,
    resolver: TrainingPlanResolver
): List<Triple<String, List<String>, List<Int>>> {
    val trainingDays = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        .filter { profile.weeklySchedule[it] == true }
    if (trainingDays.isEmpty()) return emptyList()
    return runCatching {
        TrainingSplitPlanner.build(
            trainingDays        = trainingDays,
            focusRaw            = profile.trainingFocus,
            goalRaw             = profile.goal,
            splitPreferenceRaw  = profile.splitPreference,
            experienceLevel     = profile.experienceLevel,
            age                 = profile.age,
            sexRaw              = profile.sex,
            lifestyleRaw        = profile.lifestyleActivity,
            injuries            = profile.injuries,
            sessionDurationMins = profile.sessionDurationMins,
            weekIndex           = weekIndex,
            forceDeload         = shouldForceDeload(profile.recentWorkouts)
        ).mapIndexed { si, skeleton ->
            val label = skeleton.label.trimDayPrefix()
            val usedInSession = mutableSetOf<String>()
            val exerciseIds = mutableListOf<String>()
            val setsCounts = mutableListOf<Int>()
            val recentExerciseIds = profile.recentWorkouts.flatMap { it.exercises }.map { it.exerciseId }.toSet()
            skeleton.slots.forEachIndexed { j, slot ->
                val id = resolver.resolveExerciseId(
                    rawToken            = slot.pattern.token,
                    trainingModeRaw     = profile.trainingMode,
                    equipment           = profile.equipment,
                    usedExerciseIds     = usedInSession,
                    rotationSeed        = si * 31 + j + (weekIndex * 17),
                    userExperienceLevel = profile.experienceLevel,
                    recentExerciseIds   = recentExerciseIds,
                )
                if (id != null) {
                    usedInSession += id
                    exerciseIds += id
                    setsCounts += slot.sets
                }
            }
            Triple(label, exerciseIds.toList(), setsCounts.toList())
        }
    }.getOrDefault(emptyList())
}

/** Strips the "Mon — " / "Tue — " prefix added by TrainingSplitPlanner.build(). */
private fun String.trimDayPrefix(): String {
    val dashIndex = indexOf(" — ")
    return if (dashIndex >= 0) substring(dashIndex + 3) else this
}

internal fun validateTrainingPlan(
    plan: AiTrainingResponse,
    exerciseCatalog: ExerciseCatalog = builtInExerciseCatalog,
    injuries: List<String> = emptyList()
): String? {
    if (plan.workouts.isEmpty()) return "workouts array must contain at least one workout"
    if (plan.workouts.any { it.sets.isEmpty() }) return "each workout must include at least one set"

    val forbiddenExercises = InjuryRestrictions.allForbiddenFor(injuries)
    if (forbiddenExercises.isNotEmpty()) {
        plan.workouts.forEachIndexed { wi, workout ->
            workout.sets.forEachIndexed { si, set ->
                val id = normalizeExerciseToken(set.exerciseId)
                if (id in forbiddenExercises) {
                    return "workout[$wi].sets[$si].exerciseId '$id' is contraindicated for the user's injuries"
                }
            }
        }
    }

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
    // Use previousOrSame so that on any day of the week we anchor to the CURRENT week's Monday,
    // not the NEXT Monday. This prevents normalizeWorkoutDate from rejecting dates that were
    // correctly generated for the current week (e.g. Mon–Fri) when the plan is called on Tue+.
    val today = LocalDate.now()
    val mondayOfCurrentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    return mondayOfCurrentWeek.plusWeeks(weekIndex.toLong())
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

/**
 * Returns true when the last [DELOAD_SIGNAL_WEEKS] workouts consistently show poor performance.
 * Triggers deload independently of the calendar-based fallback in SplitParamsFactory.
 * Poor performance = completion below threshold OR RPE above threshold.
 */
internal fun shouldForceDeload(recentWorkouts: List<com.vtempe.server.shared.dto.profile.AiRecentWorkout>): Boolean {
    val C = com.vtempe.server.features.ai.data.service.split.TrainingConstants
    if (recentWorkouts.size < C.DELOAD_SIGNAL_WEEKS) return false
    val lastN = recentWorkouts.takeLast(C.DELOAD_SIGNAL_WEEKS)
    return lastN.all { w ->
        w.completionRate < C.DELOAD_COMPLETION_TRIGGER ||
            (w.averageRpe != null && w.averageRpe > C.DELOAD_RPE_TRIGGER)
    }
}

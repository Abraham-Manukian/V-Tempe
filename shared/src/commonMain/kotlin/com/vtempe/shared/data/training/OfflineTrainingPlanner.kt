package com.vtempe.shared.data.training

import com.vtempe.shared.domain.exercise.ExerciseCalibrationKind
import com.vtempe.shared.domain.exercise.ExerciseLibrary
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.training.ExerciseRestrictions
import com.vtempe.shared.domain.training.MovementPattern
import com.vtempe.shared.domain.training.NoSafeTrainingPlanException
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Builds a week of workouts on the device when the AI plan is unavailable (VT-AUD-018).
 *
 * Unlike the old fixed squat/bench/deadlift template it follows the profile: the selected
 * training days, the equipment actually owned (no barbell for a home user without gear), the
 * injury restrictions shared with the server, the experience level and the session length.
 * It never invents a load — every weight is null until the user calibrates it. When not a single
 * exercise fits, it refuses with [NoSafeTrainingPlanException] instead of storing a plan.
 */
class OfflineTrainingPlanner(private val pool: List<PoolExercise> = OfflineExercisePool) {

    /** [weekStart] is the first day of the plan week (CoachSchedule.weekStart). */
    fun buildPlan(profile: Profile, weekIndex: Int, weekStart: LocalDate): TrainingPlan {
        val restrictions = ExerciseRestrictions.forInjuries(profile.constraints.injuries)
        val equipment = availableEquipment(profile)
        val slots = SlotOrder.take(slotCount(profile.sessionDurationMins))
        val maxDifficulty = (profile.experienceLevel + 1).coerceIn(1, 5)

        val workouts = trainingDates(profile, weekStart).mapIndexedNotNull { dayIndex, date ->
            val used = mutableSetOf<String>()
            val sets = slots.mapNotNull { slot ->
                (listOf(slot) + alternativesFor(slot)).firstNotNullOfOrNull { pattern ->
                    val candidates = pool
                        .filter { it.primary == pattern && it.id !in used && it.difficulty <= maxDifficulty }
                        .filter { it.equipmentAnyOf.isEmpty() || equipment == null || it.equipmentAnyOf.any(equipment::contains) }
                        .filter { restrictions.allows(it.id, it.primary, it.secondary) }
                        .sortedBy { it.priority }
                    candidates.getOrNull(dayIndex % candidates.size.coerceAtLeast(1))
                }?.let { exercise ->
                    used += exercise.id
                    WorkoutSet(exercise.id, repsFor(exercise.id, profile.experienceLevel), weightKg = null, rpe = 7.0, sets = 3)
                }
            }
            sets.takeIf { it.isNotEmpty() }?.let { Workout(id = "w_${weekIndex}_$dayIndex", date = date, sets = it) }
        }
        if (workouts.isEmpty()) {
            throw NoSafeTrainingPlanException("No offline exercise fits the profile's injuries and equipment")
        }
        return TrainingPlan(weekIndex, workouts)
    }

    /** The selected weekdays (Mon/Wed/Fri when none are set, like the server fallback) within the
     *  plan week starting [weekStart] — the same dates the server uses (VT-AUD-012). */
    private fun trainingDates(profile: Profile, weekStart: LocalDate): List<LocalDate> {
        val selected = DayKeys.filter { profile.weeklySchedule[it] == true }.ifEmpty { listOf("Mon", "Wed", "Fri") }
        return (0 until 7).map { weekStart.plus(DatePeriod(days = it)) }
            .filter { DayKeys[it.dayOfWeek.isoIndex()] in selected }
    }

    /** Null = every piece of equipment (gym), otherwise the owned equipment tags. */
    private fun availableEquipment(profile: Profile): Set<String>? {
        val tags = profile.equipment.items.mapNotNull(::canonicalEquipmentTag).toSet()
        val mode = profile.trainingMode.trim().uppercase()
        val gym = when (mode) {
            "GYM", "MIXED" -> true
            "HOME", "OUTDOOR" -> false
            else -> tags.any { it == "barbell" || it == "bench" }
        }
        return if (gym) null else tags
    }

    private fun repsFor(exerciseId: String, level: Int): Int =
        when (ExerciseLibrary.all().firstOrNull { it.supports(exerciseId) }?.calibrationKind) {
            ExerciseCalibrationKind.DURATION_SECONDS -> 30
            ExerciseCalibrationKind.DURATION_MINUTES -> 5
            else -> if (level <= 2) 12 else 10
        }

    private fun slotCount(sessionMinutes: Int): Int = when {
        sessionMinutes < 30 -> 3
        sessionMinutes < 45 -> 4
        sessionMinutes < 60 -> 5
        else -> 6
    }

    /** Same substitution order as the server's InjuryFilter.safeAlternativeFor. */
    private fun alternativesFor(pattern: MovementPattern): List<MovementPattern> = when (pattern) {
        MovementPattern.KNEE_DOMINANT, MovementPattern.SINGLE_LEG ->
            listOf(MovementPattern.HINGE, MovementPattern.HORIZONTAL_PULL, MovementPattern.CORE)
        MovementPattern.HINGE -> listOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.CORE)
        MovementPattern.VERTICAL_PUSH -> listOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.CORE)
        MovementPattern.HORIZONTAL_PUSH -> listOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.CORE)
        else -> listOf(MovementPattern.CORE, MovementPattern.MOBILITY)
    }

    private fun DayOfWeek.isoIndex(): Int = ordinal // MONDAY = 0 … SUNDAY = 6

    private companion object {
        val DayKeys = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        val SlotOrder = listOf(
            MovementPattern.KNEE_DOMINANT,
            MovementPattern.HORIZONTAL_PUSH,
            MovementPattern.HORIZONTAL_PULL,
            MovementPattern.HINGE,
            MovementPattern.CORE,
            MovementPattern.VERTICAL_PULL,
        )

        /** Same mapping as the server resolver's canonicalEquipmentTag (localized onboarding labels). */
        fun canonicalEquipmentTag(raw: String): String? {
            val token = raw.trim().lowercase().replace(' ', '_').replace('-', '_')
            if (token.isBlank()) return null
            return when {
                "dumbbell" in token || "гантел" in token -> "dumbbells"
                "barbell" in token || "штанг" in token -> "barbell"
                "kettlebell" in token || "гир" in token -> "kettlebell"
                "band" in token || "резин" in token || "эспанд" in token -> "bands"
                "bench" in token || "скам" in token -> "bench"
                "pullup" in token || "pull_up" in token || "турник" in token || "переклад" in token -> "pullup_bar"
                "trx" in token -> "trx"
                "mat" in token || "коврик" in token -> "mat"
                "cardio" in token || "кардио" in token || "bike" in token || "вел" in token ||
                    "run" in token || "бег" in token || "treadmill" in token || "дорожк" in token -> "cardio"
                else -> token
            }
        }
    }
}

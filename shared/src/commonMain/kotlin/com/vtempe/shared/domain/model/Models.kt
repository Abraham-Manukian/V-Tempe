package com.vtempe.shared.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class Goal { LOSE_FAT, GAIN_MUSCLE, MAINTAIN }
@Serializable
enum class Sex { MALE, FEMALE, OTHER }
/** Daily lifestyle activity OUTSIDE of structured workouts. */
@Serializable
enum class LifestyleActivity {
    SEDENTARY,      // desk job, mostly sitting
    LIGHT,          // some walking, light movement
    ACTIVE,         // on feet all day, waiter/teacher/nurse
    VERY_ACTIVE     // heavy physical labor, construction
}

/**
 * User-preferred weekly split structure.
 * AUTO = the server picks based on training days count.
 */
@Serializable
enum class SplitPreference {
    /** Server picks optimal split for the given day count. */
    AUTO,
    /** Full Body A/B — hit every muscle each session (2–3 days). */
    FULL_BODY,
    /** Upper / Lower split — 4 days. */
    UPPER_LOWER,
    /** Push / Pull / Legs — 5–6 days. */
    PPL
}

/**
 * How the training plan should be structured.
 * Determines rep ranges, sets, rest periods, and periodization model
 * based on ACSM/Schoenfeld/Grgic recommendations.
 */
@Serializable
enum class TrainingFocus {
    /** 3–6 reps, 80–100% 1RM, 3–5 min rest. Neural adaptation + maximal strength. */
    STRENGTH,
    /** 8–15 reps, 60–80% 1RM, 60–90s rest. Maximum muscle growth volume. */
    HYPERTROPHY,
    /** 10–15 reps, mixed approach, 90s rest. All-round health and fitness. */
    GENERAL,
    /** 12–20 reps, circuit style, 30–45s rest. Maximum calorie burn per session. */
    FAT_LOSS
}
enum class AiModelMode(val wireValue: String) {
    PAID("paid"),
    FREE("free");

    companion object {
        fun fromWire(raw: String?): AiModelMode =
            entries.firstOrNull { it.wireValue.equals(raw?.trim(), ignoreCase = true) } ?: PAID
    }
}

object CoachTrainerIds {
    const val ARTUR = "artur"
    const val MIA = "mia"
    const val VTEMPE = "vtempe"
    const val DEFAULT = MIA

    val all: List<String> = listOf(ARTUR, MIA, VTEMPE)

    fun normalize(raw: String?): String =
        all.firstOrNull { it.equals(raw?.trim(), ignoreCase = true) } ?: DEFAULT
}

@Serializable
data class Constraints(
    val injuries: List<String> = emptyList(),
    val healthNotes: List<String> = emptyList(),
)

@Serializable
data class Equipment(
    val items: List<String> = emptyList()
)

@Serializable
data class Profile(
    val id: String,
    val age: Int,
    val sex: Sex,
    val heightCm: Int,
    val weightKg: Double,
    val goal: Goal,
    val experienceLevel: Int, // 1..5
    val constraints: Constraints = Constraints(),
    val equipment: Equipment = Equipment(),
    val dietaryPreferences: List<String> = emptyList(),
    val allergies: List<String> = emptyList(),
    val weeklySchedule: Map<String, Boolean> = emptyMap(),
    val lifestyleActivity: LifestyleActivity = LifestyleActivity.SEDENTARY,
    val budgetLevel: Int = 2, // 1 low .. 3 high
    val trainingMode: String = "AUTO",
    val coachTrainerId: String = CoachTrainerIds.DEFAULT,
    val trainingFocus: TrainingFocus = TrainingFocus.GENERAL,
    val sessionDurationMins: Int = 60,
    val splitPreference: SplitPreference = SplitPreference.AUTO
)

@Serializable
data class Exercise(
    val id: String,
    val name: String,
    val muscleGroups: List<String>,
    val difficulty: Int,
    val videoUrl: String? = null,
    val technique: String? = null,
    val contraindications: List<String> = emptyList(),
)

@Serializable
data class PerformedSet(
    val setIndex: Int,
    val completed: Boolean = false,
    val completedSetsCount: Int = 0,
    val actualReps: Int? = null,
    val actualWeightKg: Double? = null,
    val actualRpe: Double? = null
)

@Serializable
data class ExtraWorkoutSet(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null
)

@Serializable
data class WorkoutProgress(
    val workoutId: String,
    val notes: String = "",
    val restSeconds: Int = 90,
    val performedSets: List<PerformedSet> = emptyList(),
    val extraSets: List<ExtraWorkoutSet> = emptyList(),
    val updatedAtEpochMs: Long = 0L,
    val submitted: Boolean = false
)

@Serializable
data class WorkoutSummary(
    val workoutId: String,
    val date: String,
    val completionRate: Double,
    val completedItems: Int,
    val plannedItems: Int,
    val totalVolumeKg: Double,
    val averageRpe: Double? = null,
    val notes: String = ""
)

@Serializable
data class WorkoutSet(
    val exerciseId: String,
    val reps: Int,
    val weightKg: Double? = null,
    val rpe: Double? = null,
    val sets: Int = 3
)

@Serializable
data class Workout(
    val id: String,
    val label: String = "",
    val date: LocalDate,
    val sets: List<WorkoutSet>
)

@Serializable
data class TrainingPlan(
    val weekIndex: Int,
    val workouts: List<Workout>
)

@Serializable
data class Macros(
    val proteinGrams: Int,
    val fatGrams: Int,
    val carbsGrams: Int,
    val kcal: Int
)

@Serializable
data class Meal(
    val name: String,
    val ingredients: List<String>,
    val kcal: Int,
    val macros: Macros
)

@Serializable
data class NutritionPlan(
    val weekIndex: Int,
    val mealsByDay: Map<String, List<Meal>>,
    val shoppingList: List<String>
)

@Serializable
data class Advice(
    val messages: List<String>,
    val disclaimer: String = "Not medical advice"
)

/** One night of logged sleep, used in the AI profile to personalise recovery advice. */
@Serializable
data class SleepEntry(val date: String, val durationMinutes: Int)

/** One body-weight measurement, used in the AI profile to track progress trends. */
@Serializable
data class WeightEntry(val date: String, val weightKg: Double)


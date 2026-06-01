package com.vtempe.shared.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
enum class Goal { LOSE_FAT, GAIN_MUSCLE, MAINTAIN }
@Serializable
enum class Sex { MALE, FEMALE, OTHER }
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
    val budgetLevel: Int = 2, // 1 low .. 3 high
    val trainingMode: String = "AUTO",
    val coachTrainerId: String = CoachTrainerIds.DEFAULT
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
    val rpe: Double? = null
)

@Serializable
data class Workout(
    val id: String,
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


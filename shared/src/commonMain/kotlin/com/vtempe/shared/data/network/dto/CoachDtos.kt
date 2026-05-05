package com.vtempe.shared.data.network.dto

import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Macros
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.TrainingPlan
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.shared.domain.model.WorkoutSummary
import com.vtempe.shared.domain.repository.CoachBundle
import com.vtempe.shared.domain.repository.CoachResponse
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
internal data class RecentWorkoutDto(
    val date: String,
    val completionRate: Double,
    val completedItems: Int,
    val plannedItems: Int,
    val totalVolumeKg: Double,
    val averageRpe: Double? = null,
    val notes: String = ""
) {
    companion object {
        fun fromDomain(summary: WorkoutSummary) = RecentWorkoutDto(
            date = summary.date,
            completionRate = summary.completionRate,
            completedItems = summary.completedItems,
            plannedItems = summary.plannedItems,
            totalVolumeKg = summary.totalVolumeKg,
            averageRpe = summary.averageRpe,
            notes = summary.notes
        )
    }
}

@Serializable
internal data class AiProfileDto(
    val age: Int,
    val sex: String,
    val heightCm: Int,
    val weightKg: Double,
    val goal: String,
    val experienceLevel: Int,
    val equipment: List<String>,
    val dietaryPreferences: List<String>,
    val allergies: List<String>,
    val weeklySchedule: Map<String, Boolean>,
    val injuries: List<String> = emptyList(),
    val healthNotes: List<String> = emptyList(),
    val budgetLevel: Int = 2,
    val trainingMode: String = "AUTO",
    val coachTrainerId: String = "mia",
    val llmMode: String? = null,
    val recentWorkouts: List<RecentWorkoutDto> = emptyList()
) {
    companion object {
        fun fromDomain(
            profile: Profile,
            llmMode: AiModelMode,
            recentWorkouts: List<WorkoutSummary> = emptyList()
        ) = AiProfileDto(
            age = profile.age,
            sex = profile.sex.name,
            heightCm = profile.heightCm,
            weightKg = profile.weightKg,
            goal = profile.goal.name,
            experienceLevel = profile.experienceLevel,
            equipment = profile.equipment.items,
            dietaryPreferences = profile.dietaryPreferences,
            allergies = profile.allergies,
            weeklySchedule = profile.weeklySchedule,
            injuries = profile.constraints.injuries,
            healthNotes = profile.constraints.healthNotes,
            budgetLevel = profile.budgetLevel,
            trainingMode = profile.trainingMode,
            coachTrainerId = profile.coachTrainerId,
            llmMode = llmMode.wireValue,
            recentWorkouts = recentWorkouts.map(RecentWorkoutDto::fromDomain)
        )
    }
}

@Serializable
internal data class AiTrainingRequestDto(
    val profile: AiProfileDto,
    val weekIndex: Int,
    val locale: String? = null
) {
    companion object {
        fun fromDomain(
            profile: Profile,
            weekIndex: Int,
            locale: String?,
            llmMode: AiModelMode,
            recentWorkouts: List<WorkoutSummary> = emptyList()
        ) = AiTrainingRequestDto(AiProfileDto.fromDomain(profile, llmMode, recentWorkouts), weekIndex, locale)
    }
}

@Serializable
internal data class AiNutritionRequestDto(
    val profile: AiProfileDto,
    val weekIndex: Int,
    val locale: String? = null
) {
    companion object {
        fun fromDomain(
            profile: Profile,
            weekIndex: Int,
            locale: String?,
            llmMode: AiModelMode,
            recentWorkouts: List<WorkoutSummary> = emptyList()
        ) = AiNutritionRequestDto(AiProfileDto.fromDomain(profile, llmMode, recentWorkouts), weekIndex, locale)
    }
}

@Serializable
internal data class AiAdviceRequestDto(
    val profile: AiProfileDto,
    val locale: String? = null
) {
    companion object {
        fun fromDomain(
            profile: Profile,
            locale: String?,
            llmMode: AiModelMode,
            recentWorkouts: List<WorkoutSummary> = emptyList()
        ) = AiAdviceRequestDto(AiProfileDto.fromDomain(profile, llmMode, recentWorkouts), locale)
    }
}

@Serializable
data class TrainingPlanDto(
    val weekIndex: Int,
    val workouts: List<WorkoutDto>
) {
    @Serializable
    data class WorkoutDto(
        val id: String,
        val date: String,
        val sets: List<SetDto>
    )

    @Serializable
    data class SetDto(
        val exerciseId: String,
        val reps: Int,
        val weightKg: Double? = null,
        val rpe: Double? = null
    )

    fun toDomain(): TrainingPlan = TrainingPlan(
        weekIndex = weekIndex,
        workouts = workouts.map { workout ->
            Workout(
                id = workout.id,
                date = LocalDate.parse(workout.date),
                sets = workout.sets.map { set ->
                    WorkoutSet(
                        exerciseId = set.exerciseId,
                        reps = set.reps,
                        weightKg = set.weightKg,
                        rpe = set.rpe
                    )
                }
            )
        }
    )

    companion object {
        fun fromDomain(plan: TrainingPlan): TrainingPlanDto {
            val workouts = plan.workouts
                .sortedBy { it.date }
                .mapIndexed { index, workout ->
                    val sanitizedId = workout.id.ifBlank { "w_${plan.weekIndex}_$index" }
                    WorkoutDto(
                        id = sanitizedId,
                        date = workout.date.toString(),
                        sets = workout.sets
                            .mapNotNull { set ->
                                val exerciseId = set.exerciseId.trim()
                                if (exerciseId.isEmpty()) return@mapNotNull null
                                SetDto(
                                    exerciseId = exerciseId,
                                    reps = set.reps,
                                    weightKg = set.weightKg,
                                    rpe = set.rpe
                                )
                            }
                    )
                }
            return TrainingPlanDto(
                weekIndex = plan.weekIndex,
                workouts = workouts
            )
        }
    }
}

@Serializable
data class NutritionPlanDto(
    val weekIndex: Int,
    val mealsByDay: Map<String, List<MealDto>>
) {
    @Serializable
    data class MealDto(
        val name: String,
        val ingredients: List<String>,
        val kcal: Int,
        val macros: Macros
    )

    fun toDomain(): NutritionPlan {
        val grouped = linkedMapOf<String, MutableList<MealDto>>()
        mealsByDay.forEach { (dayRaw, meals) ->
            val key = normalizeDayLabel(dayRaw)
            val bucket = grouped.getOrPut(key) { mutableListOf() }
            bucket += meals
        }
        val mealsDomain = grouped.mapValues { (_, meals) ->
            meals.map { meal ->
                Meal(
                    name = meal.name,
                    ingredients = meal.ingredients.map { it.trim() }.filter { it.isNotEmpty() },
                    kcal = meal.kcal,
                    macros = meal.macros
                )
            }
        }
        val shopping = grouped.values
            .flatten()
            .flatMap { it.ingredients }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
        return NutritionPlan(weekIndex, mealsDomain, shopping)
    }

    companion object {
        fun fromDomain(plan: NutritionPlan): NutritionPlanDto {
            val normalized = linkedMapOf<String, MutableList<MealDto>>()
            plan.mealsByDay.forEach { (rawDay, meals) ->
                val key = normalizeDayLabel(rawDay)
                val bucket = normalized.getOrPut(key) { mutableListOf() }
                meals.forEach { meal ->
                    val sanitizedName = meal.name.trim().ifEmpty { "Meal" }
                    val sanitizedIngredients = meal.ingredients
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    bucket += MealDto(
                        name = sanitizedName,
                        ingredients = sanitizedIngredients,
                        kcal = meal.kcal,
                        macros = meal.macros
                    )
                }
            }
            return NutritionPlanDto(
                weekIndex = plan.weekIndex,
                mealsByDay = normalized
            )
        }
    }
}

@Serializable
data class AdviceDto(
    val messages: List<String>,
    val disclaimer: String? = null
) {
    fun toDomain(): Advice = Advice(
        messages = messages,
        disclaimer = disclaimer ?: "Not medical advice"
    )

    companion object {
        fun fromDomain(advice: Advice): AdviceDto = AdviceDto(
            messages = advice.messages,
            disclaimer = advice.disclaimer
        )
    }
}

@Serializable
internal data class AiBootstrapRequestDto(
    val profile: AiProfileDto,
    val weekIndex: Int,
    val locale: String? = null
) {
    companion object {
        fun fromDomain(
            profile: Profile,
            weekIndex: Int,
            locale: String?,
            llmMode: AiModelMode,
            recentWorkouts: List<WorkoutSummary> = emptyList()
        ) = AiBootstrapRequestDto(AiProfileDto.fromDomain(profile, llmMode, recentWorkouts), weekIndex, locale)
    }
}

@Serializable
data class AiBootstrapResponseDto(
    val trainingPlan: TrainingPlanDto? = null,
    val nutritionPlan: NutritionPlanDto? = null,
    val sleepAdvice: AdviceDto? = null
) {
    fun toDomain(): CoachBundle = CoachBundle(
        trainingPlan = trainingPlan?.toDomain(),
        nutritionPlan = nutritionPlan?.toDomain(),
        sleepAdvice = sleepAdvice?.toDomain()
    )

    companion object {
        fun fromDomain(bundle: CoachBundle): AiBootstrapResponseDto = AiBootstrapResponseDto(
            trainingPlan = bundle.trainingPlan?.let { TrainingPlanDto.fromDomain(it) },
            nutritionPlan = bundle.nutritionPlan?.let { NutritionPlanDto.fromDomain(it) },
            sleepAdvice = bundle.sleepAdvice?.let { AdviceDto.fromDomain(it) }
        )
    }
}

@Serializable
data class ChatResponse(
    val reply: String,
    val trainingPlan: TrainingPlanDto? = null,
    val nutritionPlan: NutritionPlanDto? = null,
    val sleepAdvice: AdviceDto? = null,
    val actions: List<ChatActionDto> = emptyList()
) {
    fun toDomain(): CoachResponse = CoachResponse(
        reply = reply,
        trainingPlan = trainingPlan?.toDomain(),
        nutritionPlan = nutritionPlan?.toDomain(),
        sleepAdvice = sleepAdvice?.toDomain(),
        actions = actions.mapNotNull(ChatActionDto::toDomain)
    )
}

private val dayAliasMap: Map<String, String> = buildMap {
    put("mon", "Mon"); put("monday", "Mon"); put("\u043F\u043E\u043D\u0435\u0434\u0435\u043B\u044C\u043D\u0438\u043A", "Mon"); put("\u043F\u043E\u043D", "Mon"); put("\u043F\u043D", "Mon")
    put("tue", "Tue"); put("tues", "Tue"); put("tuesday", "Tue"); put("\u0432\u0442\u043E\u0440\u043D\u0438\u043A", "Tue"); put("\u0432\u0442", "Tue")
    put("wed", "Wed"); put("weds", "Wed"); put("wednesday", "Wed"); put("\u0441\u0440\u0435\u0434\u0430", "Wed"); put("\u0441\u0440", "Wed")
    put("thu", "Thu"); put("thur", "Thu"); put("thurs", "Thu"); put("thursday", "Thu"); put("\u0447\u0435\u0442\u0432\u0435\u0440\u0433", "Thu"); put("\u0447\u0442", "Thu")
    put("fri", "Fri"); put("friday", "Fri"); put("\u043F\u044F\u0442\u043D\u0438\u0446\u0430", "Fri"); put("\u043F\u0442", "Fri")
    put("sat", "Sat"); put("saturday", "Sat"); put("\u0441\u0443\u0431\u0431\u043E\u0442\u0430", "Sat"); put("\u0441\u0431", "Sat")
    put("sun", "Sun"); put("sunday", "Sun"); put("\u0432\u043E\u0441\u043A\u0440\u0435\u0441\u0435\u043D\u044C\u0435", "Sun"); put("\u0432\u0441", "Sun")
}

private val numericDayMap = mapOf(
    1 to "Mon",
    2 to "Tue",
    3 to "Wed",
    4 to "Thu",
    5 to "Fri",
    6 to "Sat",
    7 to "Sun"
)

private fun normalizeDayLabel(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "Mon"
    val lower = trimmed.lowercase()
    dayAliasMap[lower]?.let { return it }
    val numeric = Regex("(?:day|\u0434\u0435\u043D\u044C)\\s*(\\d)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Regex("^(\\d)").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()
    if (numeric != null) {
        numericDayMap[numeric]?.let { return it }
    }
    return trimmed
}


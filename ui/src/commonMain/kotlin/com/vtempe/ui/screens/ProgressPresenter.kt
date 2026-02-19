package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.shared.domain.model.Workout
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.DayOfWeek

data class ProgressState(
    val totalWorkouts: Int = 0,
    val totalSets: Int = 0,
    val totalVolume: Int = 0,
    val weeklyVolumes: List<Int> = List(7) { 0 },
    val weightSeries: List<Float> = emptyList(),
    val caloriesSeries: List<Int> = emptyList(),
    val sleepHoursWeek: List<Int> = emptyList(),
)

interface ProgressPresenter {
    val state: StateFlow<ProgressState>
}

internal fun buildProgressState(
    workouts: List<Workout>,
    nutritionPlan: NutritionPlan?
): ProgressState {
    val totalSets = workouts.sumOf { it.sets.size }
    val totalVolume = workouts.sumOf { workout ->
        workout.sets.sumOf { set -> ((set.weightKg ?: 0.0) * set.reps).toInt() }
    }

    val weeklyVolumesArray = IntArray(7) { 0 }
    workouts.forEach { workout ->
        val dayVolume = workout.sets.sumOf { set -> ((set.weightKg ?: 0.0) * set.reps).toInt() }
        weeklyVolumesArray[workout.date.dayOfWeek.weekIndex()] += dayVolume
    }

    val recentWeightSeries = workouts
        .sortedBy { it.date }
        .takeLast(14)
        .mapNotNull { workout ->
            val nonNullWeights = workout.sets.mapNotNull { it.weightKg }
            if (nonNullWeights.isEmpty()) null else nonNullWeights.average().toFloat()
        }

    val caloriesSeries = nutritionPlan
        ?.let { plan ->
            listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").map { day ->
                plan.mealsByDay[day].orEmpty().sumOf { it.kcal }
            }
        }
        .orEmpty()

    return ProgressState(
        totalWorkouts = workouts.size,
        totalSets = totalSets,
        totalVolume = totalVolume,
        weeklyVolumes = weeklyVolumesArray.toList(),
        weightSeries = recentWeightSeries,
        caloriesSeries = caloriesSeries,
        sleepHoursWeek = emptyList()
    )
}

private fun DayOfWeek.weekIndex(): Int = when (this) {
    DayOfWeek.MONDAY -> 0
    DayOfWeek.TUESDAY -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY -> 3
    DayOfWeek.FRIDAY -> 4
    DayOfWeek.SATURDAY -> 5
    DayOfWeek.SUNDAY -> 6
}

@Composable
expect fun rememberProgressPresenter(): ProgressPresenter

@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.Meal
import com.vtempe.shared.domain.model.Workout
import com.vtempe.shared.domain.model.WorkoutSet
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource

// ── Workout card ──────────────────────────────────────────────────────────────

@Composable
internal fun DayWorkoutCard(
    modifier: Modifier = Modifier,
    workouts: List<Workout>,
) {
    val accent = AiPalette.DeepAccent
    val contentColor = MaterialTheme.colorScheme.onSurface

    val totalSets = workouts.sumOf { it.sets.size }
    val totalVolume = workouts.sumOf { w ->
        w.sets.sumOf { ((it.weightKg ?: 0.0) * it.reps).toInt() }
    }
    val byExercise: Map<String, List<WorkoutSet>> =
        workouts.flatMap { it.sets }.groupBy { it.exerciseId }

    Card(
        modifier = modifier,
        colors = progressCardColors(),
        elevation = progressCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(Res.string.calendar_workout_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DaySummaryChip(stringResource(Res.string.calendar_sets).kmpFormat(totalSets))
                    DaySummaryChip(stringResource(Res.string.calendar_volume_kg).kmpFormat(totalVolume))
                }
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.10f))

            byExercise.forEach { (exerciseId, sets) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = iconFor(
                                com.vtempe.shared.domain.exercise.ExerciseLibrary
                                    .findByIdOrAlias(exerciseId)
                                    ?.visualFamily
                                    ?: com.vtempe.shared.domain.exercise.ExerciseVisualFamily.GENERIC,
                                exerciseId,
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = accent,
                        )
                        Text(
                            exerciseLabel(exerciseId),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                        )
                    }
                    sets.forEach { set ->
                        Text(
                            "· ${plannedSetSummary(set)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.75f),
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Nutrition card ────────────────────────────────────────────────────────────

@Composable
internal fun DayNutritionCard(
    modifier: Modifier = Modifier,
    meals: List<Meal>,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    val totalKcal = meals.sumOf { it.kcal }
    val totalP    = meals.sumOf { it.macros.proteinGrams }
    val totalF    = meals.sumOf { it.macros.fatGrams }
    val totalC    = meals.sumOf { it.macros.carbsGrams }

    Card(
        modifier = modifier,
        colors = progressCardColors(),
        elevation = progressCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(Res.string.calendar_nutrition_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                )
                DaySummaryChip(stringResource(Res.string.calendar_total_kcal).kmpFormat(totalKcal))
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.10f))

            meals.forEach { meal ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            meal.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${meal.kcal} kcal",
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        stringResource(Res.string.nutrition_macros_line)
                            .kmpFormat(
                                meal.kcal,
                                meal.macros.proteinGrams,
                                meal.macros.fatGrams,
                                meal.macros.carbsGrams,
                            ),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.55f),
                    )
                }
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.10f))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(Res.string.calendar_daily_totals),
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.55f),
                )
                Text(
                    stringResource(Res.string.nutrition_macros_line).kmpFormat(totalKcal, totalP, totalF, totalC),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                )
            }
        }
    }
}

// ── Summary chip ──────────────────────────────────────────────────────────────

@Composable
internal fun DaySummaryChip(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens
import com.vtempe.ui.*
import com.vtempe.ui.presenter.ProgressPresenter
import com.vtempe.ui.presenter.ProgressState

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BarChart
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.components.LineChart
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import com.vtempe.ui.util.kmpFormat
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    presenter: ProgressPresenter = rememberProgressPresenter()
) {
    val state by presenter.state.collectAsState()
    val dayLabels = listOf(
        stringResource(Res.string.day_mon_short),
        stringResource(Res.string.day_tue_short),
        stringResource(Res.string.day_wed_short),
        stringResource(Res.string.day_thu_short),
        stringResource(Res.string.day_fri_short),
        stringResource(Res.string.day_sat_short),
        stringResource(Res.string.day_sun_short)
    )
    val contentColor = MaterialTheme.colorScheme.onSurface
    val weeklyTotalVolume = state.weeklyVolumes.sum()
    val activeDays = state.weeklyVolumes.count { it > 0 }
    val avgVolumePerWorkout = if (state.totalWorkouts > 0) state.totalVolume / state.totalWorkouts else 0
    val bestDayIndex = state.weeklyVolumes.indices.maxByOrNull { state.weeklyVolumes[it] } ?: 0
    val bestDayVolume = state.weeklyVolumes.getOrNull(bestDayIndex) ?: 0
    val sleepAverage = if (state.sleepHoursWeek.isNotEmpty()) state.sleepHoursWeek.average().toFloat() else 0f
    val sleepTargetGap = 8f - sleepAverage
    val weightDelta = if (state.weightSeries.size >= 2) state.weightSeries.last() - state.weightSeries.first() else 0f
    val caloriesAverage = if (state.caloriesSeries.isNotEmpty()) state.caloriesSeries.average().roundToInt() else 0

    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    BrandScreen(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 20.dp,
                top = topBarHeight + 16.dp,
                end = 20.dp,
                bottom = bottomBarHeight + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Calendar
            item {
                val monthName = when (state.calendarMonth) {
                    1  -> stringResource(Res.string.month_1)
                    2  -> stringResource(Res.string.month_2)
                    3  -> stringResource(Res.string.month_3)
                    4  -> stringResource(Res.string.month_4)
                    5  -> stringResource(Res.string.month_5)
                    6  -> stringResource(Res.string.month_6)
                    7  -> stringResource(Res.string.month_7)
                    8  -> stringResource(Res.string.month_8)
                    9  -> stringResource(Res.string.month_9)
                    10 -> stringResource(Res.string.month_10)
                    11 -> stringResource(Res.string.month_11)
                    else -> stringResource(Res.string.month_12)
                }
                CalendarCard(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    year = state.calendarYear,
                    month = state.calendarMonth,
                    monthName = monthName,
                    today = state.today,
                    workoutDates = state.workoutDates,
                    selectedDate = state.selectedDate,
                    onPrev = { presenter.prevMonth() },
                    onNext = { presenter.nextMonth() },
                    onSelectDate = { date ->
                        if (state.selectedDate == date) presenter.clearDate()
                        else presenter.selectDate(date)
                    }
                )
            }

            // Day detail — workout card
            if (state.selectedDate != null && state.dayWorkouts.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220)) + slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(220))
                    ) {
                        DayWorkoutCard(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            workouts = state.dayWorkouts,
                        )
                    }
                }
            }

            // Day detail — nutrition card
            if (state.selectedDate != null && state.dayMeals.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(260)) + slideInVertically(initialOffsetY = { it / 4 }, animationSpec = tween(260))
                    ) {
                        DayNutritionCard(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            meals = state.dayMeals,
                        )
                    }
                }
            }

            // Day detail — empty state
            if (state.selectedDate != null && state.dayWorkouts.isEmpty() && state.dayMeals.isEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220))
                    ) {
                        Card(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            colors = progressCardColors(),
                            elevation = progressCardElevation(),
                            shape = MaterialTheme.shapes.extraLarge,
                        ) {
                            Text(
                                text = stringResource(Res.string.calendar_day_empty),
                                modifier = Modifier.padding(20.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { it / 5 },
                        animationSpec = tween(300)
                    )
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        colors = progressCardColors(),
                        elevation = progressCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(stringResource(Res.string.progress_summary_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_workouts),
                                    value = "${state.totalWorkouts}"
                                )
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_sets),
                                    value = "${state.totalSets}"
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_volume_kg),
                                    value = "${state.totalVolume}"
                                )
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_avg_volume),
                                    value = "$avgVolumePerWorkout"
                                )
                            }
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(340)) + slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec = tween(340)
                    )
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        colors = progressCardColors(),
                        elevation = progressCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(Res.string.progress_weekly_volume),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                HighlightPill(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(Res.string.progress_active_days),
                                    value = stringResource(Res.string.progress_active_days_value).kmpFormat(activeDays)
                                )
                                HighlightPill(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(Res.string.progress_best_day),
                                    value = stringResource(Res.string.progress_best_day_value).kmpFormat(dayLabels[bestDayIndex], bestDayVolume)
                                )
                                HighlightPill(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(Res.string.progress_week_total),
                                    value = stringResource(Res.string.progress_week_volume_kg).kmpFormat(weeklyTotalVolume)
                                )
                            }
                            BarChart(
                                data = state.weeklyVolumes.map { it.coerceAtLeast(0) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                dayLabels.mapIndexed { index, day ->
                                    "$day: ${state.weeklyVolumes.getOrNull(index) ?: 0}"
                                }.joinToString("   "),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }

            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(390)) + slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec = tween(390)
                    )
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        colors = progressCardColors(),
                        elevation = progressCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(Res.string.sleep_weekly_chart_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            if (state.sleepHoursWeek.isNotEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    HighlightPill(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(Res.string.progress_avg_sleep),
                                        value = stringResource(Res.string.progress_sleep_hours_short).kmpFormat(sleepAverage.roundToInt())
                                    )
                                    HighlightPill(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(Res.string.progress_sleep_goal),
                                        value = if (sleepTargetGap > 0) "-${sleepTargetGap.roundToInt()} h" else "+${abs(sleepTargetGap).roundToInt()} h"
                                    )
                                }
                                BarChart(
                                    data = state.sleepHoursWeek,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(Res.string.progress_sleep_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor.copy(alpha = 0.85f),
                                )
                            } else {
                                Text(
                                    stringResource(Res.string.progress_sleep_no_data),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor.copy(alpha = 0.85f),
                                )
                            }
                            if (state.weightSeries.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(Res.string.progress_avg_weight_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = contentColor)
                                LineChart(
                                    values = state.weightSeries,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(Res.string.progress_weight_change).kmpFormat("${if (weightDelta >= 0f) "+" else ""}${weightDelta.toOneDecimal()}"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor
                                )
                            }
                            if (state.caloriesSeries.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(Res.string.progress_calories_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = contentColor)
                                BarChart(
                                    data = state.caloriesSeries,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    stringResource(Res.string.progress_calories_avg).kmpFormat(caloriesAverage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun progressCardColors() =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun progressCardElevation() =
    CardDefaults.cardElevation(defaultElevation = 8.dp)

@Composable
private fun ProgressMetricTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun HighlightPill(
    modifier: Modifier = Modifier,
    label: String,
    value: String
) {
    Column(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun Float.toOneDecimal(): String {
    val rounded = (this * 10f).roundToInt() / 10f
    return rounded.toString()
}

@Composable
private fun CalendarCard(
    modifier: Modifier = Modifier,
    year: Int,
    month: Int,
    monthName: String,
    today: LocalDate,
    workoutDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val firstDay = LocalDate(year, month, 1)
    // isoDayNumber: Mon=1 .. Sun=7, offset to 0-based Mon=0
    // Mon=0 .. Sun=6
    val startOffset: Int = when (firstDay.dayOfWeek) {
        kotlinx.datetime.DayOfWeek.MONDAY    -> 0
        kotlinx.datetime.DayOfWeek.TUESDAY   -> 1
        kotlinx.datetime.DayOfWeek.WEDNESDAY -> 2
        kotlinx.datetime.DayOfWeek.THURSDAY  -> 3
        kotlinx.datetime.DayOfWeek.FRIDAY    -> 4
        kotlinx.datetime.DayOfWeek.SATURDAY  -> 5
        else                                  -> 6
    }
    val daysInMonth: Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> 30
    }

    Card(
        modifier = modifier,
        colors = progressCardColors(),
        elevation = progressCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Header: prev / month-year / next
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onPrev) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = onSurface)
                }
                Text(
                    "$monthName $year",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onSurface,
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = onSurface)
                }
            }

            // Day-of-week row
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su").forEach { d ->
                    Text(
                        d,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = onSurface.copy(alpha = 0.5f),
                    )
                }
            }

            // Day cells — up to 6 rows × 7 cols
            val totalCells = startOffset + daysInMonth
            val rows = (totalCells + 6) / 7
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - startOffset + 1
                        if (dayNumber < 1 || dayNumber > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = LocalDate(year, month, dayNumber)
                            val isSelected = date == selectedDate
                            val isToday = date == today
                            val hasWorkout = date in workoutDates

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> primary
                                            isToday -> primary.copy(alpha = 0.15f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .clickable { onSelectDate(date) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "$dayNumber",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else onSurface,
                                    )
                                    if (hasWorkout) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                                    else primary,
                                                    shape = CircleShape
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayWorkoutCard(
    modifier: Modifier = Modifier,
    workouts: List<com.vtempe.shared.domain.model.Workout>,
) {
    val accent = com.vtempe.core.designsystem.theme.AiPalette.DeepAccent
    val contentColor = MaterialTheme.colorScheme.onSurface

    val totalSets = workouts.sumOf { it.sets.size }
    val totalVolume = workouts.sumOf { w -> w.sets.sumOf { ((it.weightKg ?: 0.0) * it.reps).toInt() } }

    // group all sets across workouts by exerciseId
    val byExercise: Map<String, List<com.vtempe.shared.domain.model.WorkoutSet>> =
        workouts.flatMap { it.sets }.groupBy { it.exerciseId }

    Card(
        modifier = modifier,
        colors = progressCardColors(),
        elevation = progressCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header row
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

            // Exercises
            byExercise.forEach { (exerciseId, sets) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = iconFor(
                                com.vtempe.shared.domain.exercise.ExerciseLibrary
                                    .findByIdOrAlias(exerciseId)
                                    ?.visualFamily
                                    ?: com.vtempe.shared.domain.exercise.ExerciseVisualFamily.GENERIC,
                                exerciseId
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

@Composable
private fun DayNutritionCard(
    modifier: Modifier = Modifier,
    meals: List<com.vtempe.shared.domain.model.Meal>,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val primary = MaterialTheme.colorScheme.primary

    val totalKcal  = meals.sumOf { it.kcal }
    val totalP     = meals.sumOf { it.macros.proteinGrams }
    val totalF     = meals.sumOf { it.macros.fatGrams }
    val totalC     = meals.sumOf { it.macros.carbsGrams }

    Card(
        modifier = modifier,
        colors = progressCardColors(),
        elevation = progressCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header row
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

            // Meals
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
                            .kmpFormat(meal.kcal, meal.macros.proteinGrams, meal.macros.fatGrams, meal.macros.carbsGrams),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.55f),
                    )
                }
            }

            HorizontalDivider(color = contentColor.copy(alpha = 0.10f))

            // Daily totals row
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

@Composable
private fun DaySummaryChip(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

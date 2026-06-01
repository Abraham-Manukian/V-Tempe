@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import com.vtempe.ui.presenter.ProgressPresenter

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BarChart
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.components.LineChart
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    presenter: ProgressPresenter = rememberProgressPresenter()
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    val dayLabels = listOf(
        stringResource(Res.string.day_mon_short),
        stringResource(Res.string.day_tue_short),
        stringResource(Res.string.day_wed_short),
        stringResource(Res.string.day_thu_short),
        stringResource(Res.string.day_fri_short),
        stringResource(Res.string.day_sat_short),
        stringResource(Res.string.day_sun_short),
    )
    val contentColor = MaterialTheme.colorScheme.onSurface

    // Derived stats
    val weeklyTotalVolume  = state.weeklyVolumes.sum()
    val activeDays         = state.weeklyVolumes.count { it > 0 }
    val avgVolumePerWorkout = if (state.totalWorkouts > 0) state.totalVolume / state.totalWorkouts else 0
    val bestDayIndex       = state.weeklyVolumes.indices.maxByOrNull { state.weeklyVolumes[it] } ?: 0
    val bestDayVolume      = state.weeklyVolumes.getOrNull(bestDayIndex) ?: 0
    val sleepAverage       = if (state.sleepHoursWeek.isNotEmpty()) state.sleepHoursWeek.average().toFloat() else 0f
    val sleepTargetGap     = 8f - sleepAverage
    val weightDelta        = if (state.weightSeries.size >= 2) state.weightSeries.last() - state.weightSeries.first() else 0f
    val caloriesAverage    = if (state.caloriesSeries.isNotEmpty()) state.caloriesSeries.average().roundToInt() else 0

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

    BrandScreen(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(
                start = 20.dp,
                top = topBarHeight + 16.dp,
                end = 20.dp,
                bottom = bottomBarHeight + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {

            // ── Calendar ──────────────────────────────────────────
            item {
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
                    },
                )
            }

            // ── Day detail: workout ───────────────────────────────
            if (state.selectedDate != null && state.dayWorkouts.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 4 },
                    ) {
                        DayWorkoutCard(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            workouts = state.dayWorkouts,
                        )
                    }
                }
            }

            // ── Day detail: nutrition ─────────────────────────────
            if (state.selectedDate != null && state.dayMeals.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 4 },
                    ) {
                        DayNutritionCard(
                            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                            meals = state.dayMeals,
                        )
                    }
                }
            }

            // ── Day detail: empty state ───────────────────────────
            if (state.selectedDate != null && state.dayWorkouts.isEmpty() && state.dayMeals.isEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220)),
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
                                color = contentColor.copy(alpha = 0.55f),
                            )
                        }
                    }
                }
            }

            // ── Summary totals card ───────────────────────────────
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 5 },
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        colors = progressCardColors(),
                        elevation = progressCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                stringResource(Res.string.progress_summary_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_workouts),
                                    value = "${state.totalWorkouts}",
                                )
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_sets),
                                    value = "${state.totalSets}",
                                )
                            }
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_volume_kg),
                                    value = "${state.totalVolume}",
                                )
                                ProgressMetricTile(
                                    modifier = Modifier.weight(1f),
                                    title = stringResource(Res.string.progress_metric_avg_volume),
                                    value = "$avgVolumePerWorkout",
                                )
                            }
                        }
                    }
                }
            }

            // ── Weekly volume card ────────────────────────────────
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(340)) + slideInVertically(tween(340)) { it / 6 },
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        colors = progressCardColors(),
                        elevation = progressCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(Res.string.progress_weekly_volume),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                            androidx.compose.foundation.layout.Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HighlightPill(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(Res.string.progress_active_days),
                                    value = stringResource(Res.string.progress_active_days_value).kmpFormat(activeDays),
                                )
                                HighlightPill(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(Res.string.progress_best_day),
                                    value = stringResource(Res.string.progress_best_day_value).kmpFormat(dayLabels[bestDayIndex], bestDayVolume),
                                )
                                HighlightPill(
                                    modifier = Modifier.weight(1f),
                                    label = stringResource(Res.string.progress_week_total),
                                    value = stringResource(Res.string.progress_week_volume_kg).kmpFormat(weeklyTotalVolume),
                                )
                            }
                            BarChart(
                                data = state.weeklyVolumes.map { it.coerceAtLeast(0) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                dayLabels.mapIndexed { i, day ->
                                    "$day: ${state.weeklyVolumes.getOrNull(i) ?: 0}"
                                }.joinToString("   "),
                                style = MaterialTheme.typography.labelMedium,
                                color = contentColor.copy(alpha = 0.75f),
                            )
                        }
                    }
                }
            }

            // ── Sleep + weight + calories card ────────────────────
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(390)) + slideInVertically(tween(390)) { it / 6 },
                ) {
                    Card(
                        modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                        colors = progressCardColors(),
                        elevation = progressCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                stringResource(Res.string.sleep_weekly_chart_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                            if (state.sleepHoursWeek.isNotEmpty()) {
                                androidx.compose.foundation.layout.Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    HighlightPill(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(Res.string.progress_avg_sleep),
                                        value = stringResource(Res.string.progress_sleep_hours_short).kmpFormat(sleepAverage.roundToInt()),
                                    )
                                    HighlightPill(
                                        modifier = Modifier.weight(1f),
                                        label = stringResource(Res.string.progress_sleep_goal),
                                        value = if (sleepTargetGap > 0)
                                            "-${sleepTargetGap.roundToInt()} h"
                                        else
                                            "+${abs(sleepTargetGap).roundToInt()} h",
                                    )
                                }
                                BarChart(data = state.sleepHoursWeek, modifier = Modifier.fillMaxWidth())
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
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                                Text(
                                    stringResource(Res.string.progress_avg_weight_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor,
                                )
                                LineChart(values = state.weightSeries, modifier = Modifier.fillMaxWidth())
                                Text(
                                    stringResource(Res.string.progress_weight_change).kmpFormat(
                                        "${if (weightDelta >= 0f) "+" else ""}${weightDelta.toOneDecimal()}"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                )
                            }

                            if (state.caloriesSeries.isNotEmpty()) {
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
                                Text(
                                    stringResource(Res.string.progress_calories_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = contentColor,
                                )
                                BarChart(data = state.caloriesSeries, modifier = Modifier.fillMaxWidth())
                                Text(
                                    stringResource(Res.string.progress_calories_avg).kmpFormat(caloriesAverage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = contentColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

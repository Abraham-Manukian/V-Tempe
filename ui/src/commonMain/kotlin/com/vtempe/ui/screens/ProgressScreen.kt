@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens
import com.vtempe.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BarChart
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.components.LineChart
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
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

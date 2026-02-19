@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens
import com.vtempe.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BarChart
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.util.kmpFormat
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import org.jetbrains.compose.resources.stringResource

@Composable
fun SleepScreen(
    presenter: SleepPresenter = rememberSleepPresenter()
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
    
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    BrandScreen(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        colors = sleepCardColors(),
                        elevation = sleepCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                stringResource(Res.string.sleep_weekly_chart_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            BarChart(
                                data = state.weeklyHours,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                dayLabels.forEachIndexed { index, label ->
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = contentColor.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            stringResource(Res.string.sleep_hours_short).kmpFormat(
                                                state.weeklyHours.getOrNull(index) ?: 0
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = contentColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = { presenter.sync() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = sleepButtonColors(),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 8.dp
                    ),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(Res.string.sleep_sync_health))
                }
            }
            items(state.tips.size) { idx ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(250)) + slideInVertically(
                        initialOffsetY = { it / 8 },
                        animationSpec = tween(250)
                    )
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = sleepTipColors(),
                        elevation = sleepCardElevation(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(
                            state.tips[idx],
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun sleepCardColors() =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun sleepTipColors() = CardDefaults.cardColors(containerColor = Color.White)

@Composable
private fun sleepCardElevation() = CardDefaults.cardElevation(defaultElevation = 8.dp)

@Composable
private fun sleepButtonColors() =
    ButtonDefaults.buttonColors(containerColor = AiPalette.DeepAccent, contentColor = AiPalette.OnDeepAccent)

@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import com.vtempe.ui.presenter.NutritionPresenter
import com.vtempe.ui.state.UiState

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.components.Skeleton
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import org.jetbrains.compose.resources.stringResource

@Composable
fun NutritionScreen(
    onOpenMeal: (day: String, index: Int) -> Unit,
    presenter: NutritionPresenter = rememberNutritionPresenter(),
) {
    val state by presenter.state.collectAsState()
    var tab by remember { mutableStateOf(0) }

    val dayOptions = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayLabels = mapOf(
        "Mon" to stringResource(Res.string.day_mon_short),
        "Tue" to stringResource(Res.string.day_tue_short),
        "Wed" to stringResource(Res.string.day_wed_short),
        "Thu" to stringResource(Res.string.day_thu_short),
        "Fri" to stringResource(Res.string.day_fri_short),
        "Sat" to stringResource(Res.string.day_sat_short),
        "Sun" to stringResource(Res.string.day_sun_short),
    )
    val contentColor = MaterialTheme.colorScheme.onSurface
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    BrandScreen(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // The 7 day chips (~56dp each + gaps + 20dp side insets) need ~500dp to sit stretched
            // edge-to-edge in one row. Below that they don't fit, so we horizontally scroll them
            // instead of stretching (which would clip the last day). 520dp keeps a safe margin so
            // the stretched layout only kicks in on tablets / wide screens that truly fit all 7.
            val isCompactWidth = maxWidth < 520.dp

            Crossfade(
                targetState = state.ui,
                label = "nutrition-ui",
                modifier = Modifier.fillMaxSize(),
            ) { uiState ->
                when (uiState) {
                    UiState.Loading -> NutritionLoadingState(
                        topPadding = topBarHeight,
                        title = stringResource(Res.string.nutrition_loading_title),
                    )
                    is UiState.Error -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = topBarHeight + 16.dp, start = 20.dp, end = 20.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ErrorState(
                            title = stringResource(Res.string.nutrition_error_title),
                            subtitle = stringResource(Res.string.nutrition_error_hint),
                            onRetry = { presenter.refresh(force = true) }
                        )
                    }
                    is UiState.Data -> NutritionContent(
                        tab = tab,
                        onTabSelect = { tab = it },
                        plan = uiState.value,
                        selectedDay = state.selectedDay,
                        dayMacros = state.dayMacros,
                        weekMacros = state.weekMacros,
                        onDaySelect = { presenter.selectDay(it) },
                        dayOptions = dayOptions,
                        dayLabels = dayLabels,
                        contentColor = contentColor,
                        onOpenMeal = onOpenMeal,
                        topPadding = topBarHeight,
                        bottomPadding = bottomBarHeight,
                        isCompactWidth = isCompactWidth,
                    )
                }
            }
        }
    }
}

// ── Loading / error state ─────────────────────────────────────────────────────

@Composable
private fun NutritionLoadingState(
    topPadding: Dp,
    title: String,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = topPadding + 16.dp, start = 20.dp, end = 20.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = nutritionCardColors(),
            elevation = nutritionCardElevation(),
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                repeat(4) { Skeleton(height = 14.dp) }
            }
        }
    }
}

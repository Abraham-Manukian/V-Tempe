@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.vtempe.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.RingChart
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.presenter.MacroTotals
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment

@Composable
internal fun NutritionContent(
    tab: Int,
    onTabSelect: (Int) -> Unit,
    plan: NutritionPlan,
    selectedDay: String,
    dayMacros: MacroTotals,
    weekMacros: MacroTotals,
    onDaySelect: (String) -> Unit,
    dayOptions: List<String>,
    dayLabels: Map<String, String>,
    contentColor: Color,
    onOpenMeal: (day: String, index: Int) -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
    isCompactWidth: Boolean,
) {
    val dayMeals     = plan.mealsByDay[selectedDay].orEmpty()
    val proteinDay   = dayMacros.protein
    val fatDay       = dayMacros.fat
    val carbsDay     = dayMacros.carbs
    val kcalDay      = dayMacros.kcal
    val proteinWeek  = weekMacros.protein
    val fatWeek      = weekMacros.fat
    val carbsWeek    = weekMacros.carbs
    val kcalWeek     = weekMacros.kcal

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = topPadding, bottom = bottomPadding + 32.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {

        // ── Day selector ──────────────────────────────────────────
        item {
            if (isCompactWidth) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4,
                ) {
                    dayOptions.forEach { key ->
                        DayChip(
                            label = dayLabels[key] ?: key,
                            selected = selectedDay == key,
                            onClick = { onDaySelect(key) },
                        )
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                ) {
                    items(dayOptions.size) { i ->
                        val key = dayOptions[i]
                        DayChip(
                            label = dayLabels[key] ?: key,
                            selected = selectedDay == key,
                            onClick = { onDaySelect(key) },
                        )
                    }
                }
            }
        }

        // ── Tabs ──────────────────────────────────────────────────
        item {
            TabRow(
                selectedTabIndex = tab,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                containerColor = Color.Transparent,
                contentColor = Color.White,
                indicator = { positions ->
                    if (positions.isNotEmpty()) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier
                                .tabIndicatorOffset(positions[tab])
                                .padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(50))
                                .height(3.dp),
                            color = Color.White.copy(alpha = 0.95f),
                        )
                    }
                },
                divider = {},
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { onTabSelect(0) },
                    text = {
                        Text(
                            stringResource(Res.string.nutrition_tab_menu),
                            color = if (tab == 0) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { onTabSelect(1) },
                    text = {
                        Text(
                            stringResource(Res.string.nutrition_tab_shopping),
                            color = if (tab == 1) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
            }
        }

        if (tab == 1) {
            // ── Shopping list ─────────────────────────────────────
            item { Spacer(Modifier.height(12.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(250)) + slideInVertically(tween(250)) { it / 10 },
                ) {
                    ShoppingListCard(
                        items = plan.shoppingList,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                }
            }
        } else {
            // ── Macros chart ──────────────────────────────────────
            item { Spacer(Modifier.height(12.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 },
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = nutritionCardColors(),
                        elevation = nutritionCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                stringResource(Res.string.nutrition_macros_chart_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor,
                            )
                            RingChart(
                                values = listOf(proteinDay.toFloat(), fatDay.toFloat(), carbsDay.toFloat()),
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.secondary,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            val macroStats = listOf(
                                StatChipInfo(stringResource(Res.string.nutrition_macro_protein).uppercase(), stringResource(Res.string.nutrition_grams_value).kmpFormat(proteinDay), Icons.Filled.Egg),
                                StatChipInfo(stringResource(Res.string.nutrition_macro_fat).uppercase(),     stringResource(Res.string.nutrition_grams_value).kmpFormat(fatDay),     Icons.Filled.BakeryDining),
                                StatChipInfo(stringResource(Res.string.nutrition_macro_carbs).uppercase(),   stringResource(Res.string.nutrition_grams_value).kmpFormat(carbsDay),   Icons.Filled.WaterDrop),
                            )
                            StatChipGrid(stats = macroStats, columns = min(3, macroStats.size), horizontalSpacing = 16.dp)
                        }
                    }
                }
            }

            // ── Day totals ────────────────────────────────────────
            item { Spacer(Modifier.height(20.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(350, 60)) + slideInVertically(tween(350)) { it / 6 },
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = nutritionCardColors(),
                        elevation = nutritionCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(Res.string.nutrition_day_totals), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = contentColor)
                            StatChipGrid(
                                stats = listOf(
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_protein).uppercase(), stringResource(Res.string.nutrition_grams_value).kmpFormat(proteinDay), Icons.Filled.Egg),
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_fat).uppercase(),     stringResource(Res.string.nutrition_grams_value).kmpFormat(fatDay),     Icons.Filled.BakeryDining),
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_carbs).uppercase(),   stringResource(Res.string.nutrition_grams_value).kmpFormat(carbsDay),   Icons.Filled.WaterDrop),
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_kcal).uppercase(),    kcalDay.toString(),                                                       Icons.Filled.LocalFireDepartment),
                                ),
                                columns = 2,
                            )
                        }
                    }
                }
            }

            // ── Week totals ───────────────────────────────────────
            item { Spacer(Modifier.height(20.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(400, 100)) + slideInVertically(tween(400)) { it / 6 },
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = nutritionCardColors(),
                        elevation = nutritionCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge,
                    ) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(stringResource(Res.string.nutrition_week_totals), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = contentColor)
                            StatChipGrid(
                                stats = listOf(
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_protein).uppercase(), stringResource(Res.string.nutrition_grams_value).kmpFormat(proteinWeek), Icons.Filled.Egg),
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_fat).uppercase(),     stringResource(Res.string.nutrition_grams_value).kmpFormat(fatWeek),     Icons.Filled.BakeryDining),
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_carbs).uppercase(),   stringResource(Res.string.nutrition_grams_value).kmpFormat(carbsWeek),   Icons.Filled.WaterDrop),
                                    StatChipInfo(stringResource(Res.string.nutrition_macro_kcal).uppercase(),    kcalWeek.toString(),                                                       Icons.Filled.LocalFireDepartment),
                                ),
                                columns = 2,
                            )
                        }
                    }
                }
            }

            // ── Meal cards ────────────────────────────────────────
            item { Spacer(Modifier.height(20.dp)) }
            itemsIndexed(dayMeals) { index, meal ->
                val accent = macroAccentPalette[index % macroAccentPalette.size]
                Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 8 },
                    ) {
                        NutritionMealCard(
                            meal = meal,
                            accentColor = accent,
                            onClick = { onOpenMeal(selectedDay, index) },
                        )
                    }
                }
            }
        }
    }
}

// ── Shopping list card (extracted from inline) ────────────────────────────────

@Composable
private fun ShoppingListCard(items: List<String>, modifier: Modifier = Modifier) {
    val bulletColor = AiPalette.Primary
    val textColor = Color(0xFF2D2D2D)
    Card(
        modifier = modifier,
        colors = nutritionCardColors(),
        elevation = nutritionCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                stringResource(Res.string.nutrition_tab_shopping),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = textColor,
            )
            items.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(bulletColor.copy(alpha = 0.9f)),
                    )
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (index != items.lastIndex) {
                    Divider(color = textColor.copy(alpha = 0.1f))
                }
            }
        }
    }
}

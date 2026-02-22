@file:OptIn(
    org.jetbrains.compose.resources.ExperimentalResourceApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.vtempe.ui.screens
import com.vtempe.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.components.PlaceholderScreen
import com.vtempe.core.designsystem.components.RingChart
import com.vtempe.core.designsystem.components.StatChip
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.model.NutritionPlan
import com.vtempe.ui.state.UiState
import com.vtempe.ui.util.kmpFormat
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import org.jetbrains.compose.resources.stringResource
import kotlin.math.min

@Composable
fun NutritionScreen(
    onOpenMeal: (day: String, index: Int) -> Unit,
    presenter: NutritionPresenter = rememberNutritionPresenter()
) {
    val state by presenter.state.collectAsState()
    val dayOptions = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayLabels = mapOf(
        "Mon" to stringResource(Res.string.day_mon_short),
        "Tue" to stringResource(Res.string.day_tue_short),
        "Wed" to stringResource(Res.string.day_wed_short),
        "Thu" to stringResource(Res.string.day_thu_short),
        "Fri" to stringResource(Res.string.day_fri_short),
        "Sat" to stringResource(Res.string.day_sat_short),
        "Sun" to stringResource(Res.string.day_sun_short)
    )
    val contentColor = MaterialTheme.colorScheme.onSurface
    var tab by remember { mutableStateOf(0) }
    
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    BrandScreen(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isCompactWidth = maxWidth < 360.dp
            
            Crossfade(targetState = state.ui, label = "nutrition-ui", modifier = Modifier.fillMaxSize()) { uiState ->
                when (uiState) {
                    UiState.Loading -> PlaceholderScreen(
                        title = stringResource(Res.string.nutrition_loading_title),
                        sections = listOf(":")
                    )

                    is UiState.Error -> PlaceholderScreen(
                        title = stringResource(Res.string.nutrition_error_title),
                        sections = listOf(stringResource(Res.string.nutrition_error_hint))
                    )

                    is UiState.Data -> NutritionContent(
                        tab = tab,
                        onTabSelect = { tab = it },
                        plan = uiState.value,
                        selectedDay = state.selectedDay,
                        onDaySelect = { presenter.selectDay(it) },
                        dayOptions = dayOptions,
                        dayLabels = dayLabels,
                        contentColor = contentColor,
                        onOpenMeal = onOpenMeal,
                        topPadding = topBarHeight,
                        bottomPadding = bottomBarHeight,
                        isCompactWidth = isCompactWidth
                    )
                }
            }
        }
    }
}

@Composable
private fun NutritionContent(
    tab: Int,
    onTabSelect: (Int) -> Unit,
    plan: NutritionPlan,
    selectedDay: String,
    onDaySelect: (String) -> Unit,
    dayOptions: List<String>,
    dayLabels: Map<String, String>,
    contentColor: Color,
    onOpenMeal: (day: String, index: Int) -> Unit,
    topPadding: Dp,
    bottomPadding: Dp,
    isCompactWidth: Boolean
) {
    val dayMeals = plan.mealsByDay[selectedDay].orEmpty()
    val proteinDay = dayMeals.sumOf { it.macros.proteinGrams }
    val fatDay = dayMeals.sumOf { it.macros.fatGrams }
    val carbsDay = dayMeals.sumOf { it.macros.carbsGrams }
    val kcalDay = dayMeals.sumOf { it.macros.kcal }
    val allMeals = plan.mealsByDay.values.flatten()
    val proteinWeek = allMeals.sumOf { it.macros.proteinGrams }
    val fatWeek = allMeals.sumOf { it.macros.fatGrams }
    val carbsWeek = allMeals.sumOf { it.macros.carbsGrams }
    val kcalWeek = allMeals.sumOf { it.macros.kcal }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = topPadding,
            bottom = bottomPadding + 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp) // Spacing handled inside items for the headers
    ) {
        // --- Р”РЅРё РЅРµРґРµР»Рё (Hide when scrolling) ---
        item {
            if (isCompactWidth) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 20.dp, end = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 4
                ) {
                    dayOptions.forEach { key ->
                        DayChip(
                            label = dayLabels[key] ?: key,
                            selected = selectedDay == key,
                            onClick = { onDaySelect(key) }
                        )
                    }
                }
            } else {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp)
                ) {
                    items(dayOptions.size) { index ->
                        val key = dayOptions[index]
                        DayChip(
                            label = dayLabels[key] ?: key,
                            selected = selectedDay == key,
                            onClick = { onDaySelect(key) }
                        )
                    }
                }
            }
        }

        // --- Tabs (Hide when scrolling) ---
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
                        TabRowDefaults.Indicator(
                            modifier = Modifier
                                .tabIndicatorOffset(positions[tab])
                                .padding(horizontal = 24.dp)
                                .clip(RoundedCornerShape(50))
                                .height(3.dp),
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }
                },
                divider = {}
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = { onTabSelect(0) },
                    text = {
                        Text(
                            stringResource(Res.string.nutrition_tab_menu),
                            color = if (tab == 0) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { onTabSelect(1) },
                    text = {
                        Text(
                            stringResource(Res.string.nutrition_tab_shopping),
                            color = if (tab == 1) Color.White else Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }

        if (tab == 1) {
            val items = plan.shoppingList
            item { Spacer(Modifier.height(12.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(250)) + slideInVertically(
                        initialOffsetY = { it / 10 },
                        animationSpec = tween(250)
                    )
                ) {
                    val bulletColor = AiPalette.Primary
                    val textColor = Color(0xFF2D2D2D)
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = nutritionCardColors(),
                        elevation = nutritionCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                stringResource(Res.string.nutrition_tab_shopping),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = textColor
                            )
                            items.forEachIndexed { index, entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(bulletColor.copy(alpha = 0.9f))
                                    )
                                    Text(
                                        text = entry,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (index != items.lastIndex) {
                                    Divider(color = textColor.copy(alpha = 0.1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Tab 0: Menu Content
            item { Spacer(Modifier.height(12.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300)) + slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec = tween(300)
                    )
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = nutritionCardColors(),
                        elevation = nutritionCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                stringResource(Res.string.nutrition_macros_chart_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            RingChart(
                                values = listOf(
                                    proteinDay.toFloat(),
                                    fatDay.toFloat(),
                                    carbsDay.toFloat()
                                ),
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.secondary
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            val macroStats = listOf(
                                StatChipInfo(
                                    stringResource(Res.string.nutrition_macro_protein).uppercase(),
                                    stringResource(Res.string.nutrition_grams_value).kmpFormat(proteinDay),
                                    Icons.Filled.Egg
                                ),
                                StatChipInfo(
                                    stringResource(Res.string.nutrition_macro_fat).uppercase(),
                                    stringResource(Res.string.nutrition_grams_value).kmpFormat(fatDay),
                                    Icons.Filled.BakeryDining
                                ),
                                StatChipInfo(
                                    stringResource(Res.string.nutrition_macro_carbs).uppercase(),
                                    stringResource(Res.string.nutrition_grams_value).kmpFormat(carbsDay),
                                    Icons.Filled.WaterDrop
                                )
                            )
                            StatChipGrid(
                                stats = macroStats,
                                columns = min(3, macroStats.size),
                                horizontalSpacing = 16.dp
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(350, delayMillis = 60)) + slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec = tween(350)
                    )
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = nutritionCardColors(),
                        elevation = nutritionCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                stringResource(Res.string.nutrition_day_totals),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            StatChipGrid(
                                stats = listOf(
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_protein).uppercase(),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(proteinDay),
                                        Icons.Filled.Egg
                                    ),
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_fat).uppercase(),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(fatDay),
                                        Icons.Filled.BakeryDining
                                    ),
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_carbs).uppercase(),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(carbsDay),
                                        Icons.Filled.WaterDrop
                                    ),
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_kcal).uppercase(),
                                        kcalDay.toString(),
                                        Icons.Filled.LocalFireDepartment
                                    )
                                ),
                                columns = 2
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
            item {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(400, delayMillis = 100)) + slideInVertically(
                        initialOffsetY = { it / 6 },
                        animationSpec = tween(400)
                    )
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        colors = nutritionCardColors(),
                        elevation = nutritionCardElevation(),
                        shape = MaterialTheme.shapes.extraLarge
                    ) {
                        Column(
                            Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                stringResource(Res.string.nutrition_week_totals),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                            StatChipGrid(
                                stats = listOf(
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_protein).uppercase(),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(proteinWeek),
                                        Icons.Filled.Egg
                                    ),
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_fat).uppercase(),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(fatWeek),
                                        Icons.Filled.BakeryDining
                                    ),
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_carbs).uppercase(),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(carbsWeek),
                                        Icons.Filled.WaterDrop
                                    ),
                                    StatChipInfo(
                                        stringResource(Res.string.nutrition_macro_kcal).uppercase(),
                                        kcalWeek.toString(),
                                        Icons.Filled.LocalFireDepartment
                                    )
                                ),
                                columns = 2
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
            itemsIndexed(dayMeals) { index, meal ->
                val accent = macroAccentPalette[index % macroAccentPalette.size]
                Box(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(350)) + slideInVertically(
                            initialOffsetY = { it / 8 },
                            animationSpec = tween(350)
                        )
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenMeal(selectedDay, index) },
                            colors = nutritionCardColors(),
                            elevation = nutritionCardElevation(),
                            shape = MaterialTheme.shapes.extraLarge
                        ) {
                            Column(
                                Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .height(48.dp)
                                                .width(6.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(accent)
                                        )
                                        Column {
                                            Text(
                                                meal.name,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2D2D2D),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                stringResource(Res.string.nutrition_meal_meta).kmpFormat(
                                                    meal.ingredients.size,
                                                    meal.macros.kcal
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color(0xFF4B4B4B)
                                            )
                                        }
                                    }
                                    Text(
                                        stringResource(Res.string.nutrition_kcal_value).kmpFormat(meal.kcal),
                                        modifier = Modifier.widthIn(min = 68.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        textAlign = TextAlign.End,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MacroPill(
                                        stringResource(Res.string.nutrition_macro_protein),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(meal.macros.proteinGrams),
                                        accent
                                    )
                                    MacroPill(
                                        stringResource(Res.string.nutrition_macro_fat),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(meal.macros.fatGrams),
                                        MaterialTheme.colorScheme.secondary
                                    )
                                    MacroPill(
                                        stringResource(Res.string.nutrition_macro_carbs),
                                        stringResource(Res.string.nutrition_grams_value).kmpFormat(meal.macros.carbsGrams),
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                Text(
                                    text = stringResource(Res.string.nutrition_ingredients) + ": " + meal.ingredients.joinToString(),
                                    style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                                    color = Color(0xFF3A3A3A)
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
private fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.9f else 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun MacroPill(
    label: String,
    value: String,
    accent: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF646A81))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D2D2D)
            )
        }
    }
}

private data class StatChipInfo(
    val label: String,
    val value: String,
    val icon: ImageVector
)

@Composable
private fun StatChipGrid(
    stats: List<StatChipInfo>,
    columns: Int,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp
) {
    Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        stats.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing)
            ) {
                rowItems.forEach { stat ->
                    StatChip(
                        label = stat.label,
                        value = stat.value,
                        icon = stat.icon,
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun nutritionCardColors() =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
private fun nutritionCardElevation() =
    CardDefaults.cardElevation(defaultElevation = 10.dp)

private val macroAccentPalette = listOf(
    Color(0xFFB388FF),
    Color(0xFF80DEEA),
    Color(0xFFFFB74D),
    Color(0xFFFF8A80)
)

@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens
import com.vtempe.ui.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.ui.state.UiState
import com.vtempe.ui.util.kmpFormat
import com.vtempe.ui.LocalBottomBarHeight
import com.vtempe.ui.LocalTopBarHeight
import org.jetbrains.compose.resources.stringResource

@Composable
fun NutritionDetailScreen(
    day: String,
    index: Int,
    onBack: () -> Unit,
    presenter: NutritionPresenter = rememberNutritionPresenter()
) {
    val state by presenter.state.collectAsState()
    val ui = state.ui
    
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // РћС‚СЃС‚СѓРї РїРѕРґ "РїР°СЂСЏС‰РёР№" С‚РѕРї Р±Р°СЂ
            Spacer(Modifier.height(topBarHeight + 16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    onClick = onBack,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Text(
                    stringResource(Res.string.nutrition_detail_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            when (ui) {
                UiState.Loading -> Text(stringResource(Res.string.loading), color = MaterialTheme.colorScheme.onBackground)
                is UiState.Error -> Text(
                    stringResource(Res.string.nutrition_error_title),
                    color = MaterialTheme.colorScheme.error
                )
                is UiState.Data -> {
                    val meals = ui.value.mealsByDay[day].orEmpty()
                    val meal = meals.getOrNull(index)
                    if (meal == null) {
                        Text(stringResource(Res.string.nutrition_detail_missing), color = MaterialTheme.colorScheme.onBackground)
                    } else {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(300)) + slideInVertically(
                                initialOffsetY = { it / 6 },
                                animationSpec = tween(300)
                            )
                        ) {
                            Card(
                                Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                shape = MaterialTheme.shapes.extraLarge,
                                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                            ) {
                                Column(
                                    Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        meal.name,
                                        color = Color(0xFF1A1A1A),
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        stringResource(Res.string.nutrition_kcal_value).kmpFormat(meal.kcal),
                                        color = Color(0xFF323232),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        stringResource(Res.string.nutrition_macros_detail).kmpFormat(
                                            meal.macros.proteinGrams,
                                            meal.macros.fatGrams,
                                            meal.macros.carbsGrams
                                        ),
                                        color = Color(0xFF3A3A3A)
                                    )
                                    Text(
                                        stringResource(Res.string.nutrition_ingredients),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color(0xFF1A1A1A),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    meal.ingredients.forEach { ing ->
                                        Text("- $ing", color = Color(0xFF3A3A3A))
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // РћС‚СЃС‚СѓРї РїРѕРґ "РїР°СЂСЏС‰РёР№" Р±РѕС‚С‚РѕРј Р±Р°СЂ
            Spacer(Modifier.height(bottomBarHeight + 32.dp))
        }
    }
}

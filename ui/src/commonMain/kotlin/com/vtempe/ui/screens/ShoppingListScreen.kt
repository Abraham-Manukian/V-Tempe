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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.ui.state.UiState
import org.jetbrains.compose.resources.stringResource

@Composable
fun ShoppingListScreen(
    onBack: () -> Unit,
    presenter: NutritionPresenter = rememberNutritionPresenter()
) {
    val state by presenter.state.collectAsState()

    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AiPalette.DeepAccent,
                    contentColor = AiPalette.OnDeepAccent
                ),
                shape = MaterialTheme.shapes.large
            ) {
                Text(stringResource(Res.string.action_back), fontWeight = FontWeight.Bold)
            }
            when (val ui = state.ui) {
                UiState.Loading -> Text(stringResource(Res.string.loading), color = MaterialTheme.colorScheme.onBackground)
                is UiState.Error -> Text(
                    stringResource(Res.string.nutrition_error_title),
                    color = MaterialTheme.colorScheme.error
                )
                is UiState.Data -> {
                    val items = ui.value.shoppingList
                    Text(
                        stringResource(Res.string.nutrition_tab_shopping),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items) { entry ->
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(tween(250)) + slideInVertically(
                                    initialOffsetY = { it / 10 },
                                    animationSpec = tween(250)
                                )
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                    shape = MaterialTheme.shapes.large,
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                                ) {
                                    Text(
                                        text = "- $entry",
                                        modifier = Modifier.padding(16.dp),
                                        color = MaterialTheme.colorScheme.onBackground,
                                        style = MaterialTheme.typography.bodyLarge
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

@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vtempe.shared.domain.model.Meal
import com.vtempe.ui.Res
import com.vtempe.ui.*
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun NutritionMealCard(
    meal: Meal,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = nutritionCardColors(),
        elevation = nutritionCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── Name + kcal ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .height(48.dp)
                            .widthIn(min = 6.dp, max = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accentColor),
                    )
                    Column {
                        Text(
                            meal.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2D2D2D),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(Res.string.nutrition_meal_meta).kmpFormat(
                                meal.ingredients.size,
                                meal.macros.kcal,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4B4B4B),
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
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // ── Macros row ────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MacroPill(
                    label = stringResource(Res.string.nutrition_macro_protein),
                    value = stringResource(Res.string.nutrition_grams_value).kmpFormat(meal.macros.proteinGrams),
                    accent = accentColor,
                )
                MacroPill(
                    label = stringResource(Res.string.nutrition_macro_fat),
                    value = stringResource(Res.string.nutrition_grams_value).kmpFormat(meal.macros.fatGrams),
                    accent = MaterialTheme.colorScheme.secondary,
                )
                MacroPill(
                    label = stringResource(Res.string.nutrition_macro_carbs),
                    value = stringResource(Res.string.nutrition_grams_value).kmpFormat(meal.macros.carbsGrams),
                    accent = MaterialTheme.colorScheme.tertiary,
                )
            }

            // ── Ingredients ───────────────────────────────────────
            Text(
                text = stringResource(Res.string.nutrition_ingredients) + ": " + meal.ingredients.joinToString(),
                style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
                color = Color(0xFF3A3A3A),
            )
        }
    }
}

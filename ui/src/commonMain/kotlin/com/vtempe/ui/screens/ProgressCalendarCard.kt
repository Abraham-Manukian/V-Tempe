@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

@Composable
internal fun CalendarCard(
    modifier: Modifier = Modifier,
    year: Int,
    month: Int,
    monthName: String,
    today: LocalDate,
    workoutDates: Set<LocalDate>,
    nutritionDates: Set<LocalDate> = emptySet(),
    selectedDate: LocalDate?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    val firstDay = LocalDate(year, month, 1)
    val startOffset: Int = when (firstDay.dayOfWeek) {
        DayOfWeek.MONDAY    -> 0
        DayOfWeek.TUESDAY   -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY  -> 3
        DayOfWeek.FRIDAY    -> 4
        DayOfWeek.SATURDAY  -> 5
        else                -> 6  // SUNDAY
    }
    val daysInMonth: Int = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11            -> 30
        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
        else -> 30
    }

    Card(
        modifier = modifier,
        colors = progressCardColors(),
        elevation = progressCardElevation(),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Month nav header ──────────────────────────────────
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

            // ── Day-of-week header ────────────────────────────────
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

            // ── Day cells ─────────────────────────────────────────
            val rows = (startOffset + daysInMonth + 6) / 7
            for (row in 0 until rows) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val dayNumber = row * 7 + col - startOffset + 1
                        if (dayNumber < 1 || dayNumber > daysInMonth) {
                            Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                        } else {
                            val date = LocalDate(year, month, dayNumber)
                            val isSelected   = date == selectedDate
                            val isToday      = date == today
                            val hasWorkout   = date in workoutDates
                            val hasNutrition = date in nutritionDates
                            val dotColor     = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f) else primary

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> primary
                                            isToday    -> primary.copy(alpha = 0.15f)
                                            else       -> Color.Transparent
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
                                    // Dots row: workout dot (large) and/or nutrition dot (small)
                                    if (hasWorkout || hasNutrition) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (hasWorkout) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(4.dp)
                                                        .background(dotColor, CircleShape)
                                                )
                                            }
                                            if (hasNutrition && !hasWorkout) {
                                                // Nutrition-only day: smaller secondary dot
                                                Box(
                                                    modifier = Modifier
                                                        .size(3.dp)
                                                        .background(
                                                            if (isSelected) dotColor
                                                            else primary.copy(alpha = 0.45f),
                                                            CircleShape
                                                        )
                                                )
                                            } else if (hasNutrition) {
                                                // Both: add a small secondary dot next to workout dot
                                                Box(
                                                    modifier = Modifier
                                                        .size(3.dp)
                                                        .background(
                                                            if (isSelected) dotColor
                                                            else primary.copy(alpha = 0.45f),
                                                            CircleShape
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
    }
}

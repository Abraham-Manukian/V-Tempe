@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.StatChip

// ── Shared card styling ───────────────────────────────────────────────────────

@Composable
internal fun nutritionCardColors(): CardColors =
    CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f))

@Composable
internal fun nutritionCardElevation(): CardElevation =
    CardDefaults.cardElevation(defaultElevation = 10.dp)

internal val macroAccentPalette = listOf(
    Color(0xFF80CBC4), // teal
    Color(0xFF80DEEA), // cyan
    Color(0xFFFFB74D), // amber
    Color(0xFFFF8A80), // coral
)

// ── Day chip ──────────────────────────────────────────────────────────────────

@Composable
internal fun DayChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = if (selected) 0.9f else 0.25f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
        )
    }
}

// ── Macro pill ────────────────────────────────────────────────────────────────

@Composable
internal fun MacroPill(label: String, value: String, accent: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color(0xFF646A81))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF2D2D2D),
            )
        }
    }
}

// ── Stat chip grid ────────────────────────────────────────────────────────────

internal data class StatChipInfo(
    val label: String,
    val value: String,
    val icon: ImageVector,
)

@Composable
internal fun StatChipGrid(
    stats: List<StatChipInfo>,
    columns: Int,
    horizontalSpacing: Dp = 12.dp,
    verticalSpacing: Dp = 12.dp,
) {
    Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
        stats.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
            ) {
                rowItems.forEach { stat ->
                    StatChip(
                        label = stat.label,
                        value = stat.value,
                        icon = stat.icon,
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

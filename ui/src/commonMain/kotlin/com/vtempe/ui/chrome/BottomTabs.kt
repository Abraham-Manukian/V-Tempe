package com.vtempe.ui.chrome

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.icons.AiIcons
import com.vtempe.ui.*
import com.vtempe.ui.navigation.Destination
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun BottomTabs(
    selected: Destination,
    onSelect: (Destination) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomDestinations.forEach { destination ->
            val isSelected = destination.dest == selected
            val label = stringResource(destination.labelRes)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(destination.dest) }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = destination.icon,
                    contentDescription = label,
                    tint = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private data class BottomDestination(
    val dest: Destination,
    val labelRes: StringResource,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(Destination.Home, Res.string.nav_home, AiIcons.Dashboard),
    BottomDestination(Destination.Workout, Res.string.nav_workout, AiIcons.Strength),
    BottomDestination(Destination.Nutrition, Res.string.nav_nutrition, AiIcons.Nutrition),
    BottomDestination(Destination.Sleep, Res.string.nav_sleep, AiIcons.Sleep),
    BottomDestination(Destination.Progress, Res.string.nav_progress, AiIcons.Progress)
)

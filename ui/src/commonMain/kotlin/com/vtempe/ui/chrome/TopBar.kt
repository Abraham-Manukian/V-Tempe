package com.vtempe.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vtempe.ui.*
import com.vtempe.ui.navigation.Destination
import com.vtempe.ui.navigation.isBottomNav
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TopBar(
    current: Destination,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit,
) {
    val currentTitle = when (current) {
        is Destination.Home          -> stringResource(Res.string.app_name)
        is Destination.Settings      -> stringResource(Res.string.settings_title)
        is Destination.Nutrition     -> stringResource(Res.string.nav_nutrition)
        is Destination.Chat          -> stringResource(Res.string.chat_title)
        is Destination.Workout       -> stringResource(Res.string.nav_workout)
        is Destination.Sleep         -> stringResource(Res.string.nav_sleep)
        is Destination.Paywall       -> stringResource(Res.string.paywall_title)
        is Destination.Progress      -> stringResource(Res.string.nav_progress)
        is Destination.EditProfile   -> stringResource(Res.string.edit_profile_title)
        is Destination.ShoppingList  -> stringResource(Res.string.nutrition_tab_shopping)
        is Destination.NutritionDetail -> stringResource(Res.string.nutrition_detail_title)
        is Destination.ExerciseLibrary -> stringResource(Res.string.exlib_title)
        is Destination.Auth -> stringResource(Res.string.auth_title)
        else                         -> stringResource(Res.string.app_name)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Centered title (absolute) — padding keeps it away from 48dp icons on each side
        Text(
            text = currentTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 56.dp)
        )

        // Left/right icons on top of the title layer
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (canGoBack) {
                TopBarIcon(Icons.AutoMirrored.Default.ArrowBack, stringResource(Res.string.action_back), onClick = onBack)
            } else {
                TopBarIcon(Icons.AutoMirrored.Default.Chat, stringResource(Res.string.chat_title), onClick = { onNavigate(Destination.Chat) })
            }

            Row {
                if (current.isBottomNav) {
                    TopBarIcon(Icons.Default.Star, stringResource(Res.string.paywall_title), onClick = { onNavigate(Destination.Paywall) })
                }
                TopBarIcon(Icons.Default.Settings, stringResource(Res.string.settings_title), onClick = { onNavigate(Destination.Settings) })
            }
        }
    }
}

@Composable
private fun TopBarIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .background(Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

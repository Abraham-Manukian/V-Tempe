package com.vtempe.ui

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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.icons.AiIcons
import com.vtempe.ui.navigation.Destination
import com.vtempe.ui.navigation.isBottomNav
import com.vtempe.ui.screens.*
import com.vtempe.ui.theme.VTempeTheme
import com.vtempe.ui.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Глобальные провайдеры для высот баров, чтобы использовать в экранах
val LocalTopBarHeight = compositionLocalOf { 0.dp }
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

@Composable
fun AppRoot() {
    VTempeTheme {
        var currentDest by remember { mutableStateOf<Destination>(Destination.Splash) }
        var pendingChatPrompt by remember { mutableStateOf<String?>(null) }
        var isActiveWorkout by remember { mutableStateOf(false) }
        val isTabRoute = currentDest.isBottomNav
        val showTopBar = currentDest !is Destination.Onboarding &&
                currentDest !is Destination.Splash && !isActiveWorkout

        LaunchedEffect(currentDest) {
            if (currentDest !is Destination.Workout) isActiveWorkout = false
        }

        val density = LocalDensity.current
        var topBarHeight by remember { mutableStateOf(0.dp) }
        var bottomBarHeight by remember { mutableStateOf(0.dp) }

        // Пробрасываем высоты баров вниз по дереву
        CompositionLocalProvider(
            LocalTopBarHeight provides topBarHeight,
            LocalBottomBarHeight provides bottomBarHeight
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppBackground()

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AppNavigationHost(
                            current = currentDest,
                            pendingChatPrompt = pendingChatPrompt,
                            onPromptConsumed = { pendingChatPrompt = null },
                            onNavigate = { dest -> currentDest = dest },
                            onAskCoach = { prompt ->
                                pendingChatPrompt = prompt
                                currentDest = Destination.Chat
                            },
                            onEnterActiveWorkout = { isActiveWorkout = true },
                            onExitActiveWorkout = { isActiveWorkout = false }
                        )
                    }
                }

                // Top Bar
                if (showTopBar) {
                    GlassTopBarContainer(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .onGloballyPositioned { coords ->
                                topBarHeight = with(density) { coords.size.height.toDp() }
                            }
                    ) {
                        TopBar(
                            current = currentDest,
                            onNavigate = { currentDest = it }
                        )
                    }
                } else if (topBarHeight != 0.dp) {
                    topBarHeight = 0.dp
                }

                // Bottom Bar
                if (isTabRoute) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 14.dp)
                            .onGloballyPositioned { coords ->
                                bottomBarHeight = with(density) { coords.size.height.toDp() }
                            }
                    ) {
                        GlassBottomBarContainer {
                            BottomTabs(
                                selected = currentDest,
                                onSelect = { currentDest = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNavigationHost(
    current: Destination,
    pendingChatPrompt: String?,
    onPromptConsumed: () -> Unit,
    onAskCoach: (String) -> Unit,
    onNavigate: (Destination) -> Unit,
    onEnterActiveWorkout: () -> Unit,
    onExitActiveWorkout: () -> Unit
) {
    when (current) {
        is Destination.Splash -> SplashScreen(onReady = onNavigate)
        is Destination.Onboarding -> OnboardingScreen(onDone = { onNavigate(Destination.Home) })
        is Destination.Home -> HomeScreen(onNavigate = onNavigate)
        is Destination.Workout -> WorkoutScreen(
            onAskCoach = onAskCoach,
            onEnterActiveWorkout = onEnterActiveWorkout,
            onExitActiveWorkout = onExitActiveWorkout
        )
        is Destination.Nutrition -> NutritionScreen(onOpenMeal = { day, index ->
            onNavigate(Destination.NutritionDetail(day, index))
        })
        is Destination.NutritionDetail -> NutritionDetailScreen(
            day = current.day,
            index = current.index,
            onBack = { onNavigate(Destination.Nutrition) }
        )
        is Destination.Sleep -> SleepScreen()
        is Destination.Progress -> ProgressScreen()
        is Destination.Paywall -> PaywallScreen()
        is Destination.Settings -> SettingsScreen(onEditProfile = { onNavigate(Destination.EditProfile) })
        is Destination.EditProfile -> EditProfileScreen(onDone = { onNavigate(Destination.Settings) })
        is Destination.Chat -> ChatScreen(
            initialPrompt = pendingChatPrompt,
            onPromptConsumed = onPromptConsumed
        )
        is Destination.ShoppingList -> ShoppingListScreen(onBack = { onNavigate(Destination.Nutrition) })
    }
}

@Composable
private fun BottomTabs(
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

@Composable
private fun TopBar(
    current: Destination,
    onNavigate: (Destination) -> Unit
) {
    val currentTitle = when (current) {
        is Destination.Home -> stringResource(Res.string.app_name)
        is Destination.Settings -> stringResource(Res.string.settings_title)
        is Destination.Nutrition -> stringResource(Res.string.nav_nutrition)
        is Destination.Chat -> stringResource(Res.string.chat_title)
        is Destination.Workout -> stringResource(Res.string.nav_workout)
        is Destination.Sleep -> stringResource(Res.string.nav_sleep)
        is Destination.Paywall -> stringResource(Res.string.paywall_title)
        is Destination.Progress -> stringResource(Res.string.nav_progress)
        is Destination.EditProfile -> stringResource(Res.string.edit_profile_title)
        is Destination.ShoppingList -> stringResource(Res.string.nutrition_tab_shopping)
        is Destination.NutritionDetail -> stringResource(Res.string.nutrition_detail_title)
        else -> stringResource(Res.string.app_name)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarIcon(Icons.AutoMirrored.Default.Chat, onClick = { onNavigate(Destination.Chat) })

        if (!current.isBottomNav) {
            TopBarIcon(Icons.AutoMirrored.Default.ArrowBack, onClick = { onNavigate(Destination.Home) })
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = currentTitle,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.weight(1f))

        TopBarIcon(Icons.Default.Star, onClick = { onNavigate(Destination.Paywall) })
        TopBarIcon(Icons.Default.Settings, onClick = { onNavigate(Destination.Settings) })
    }
}

@Composable
private fun TopBarIcon(
    imageVector: ImageVector,
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
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
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



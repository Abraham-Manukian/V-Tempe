package com.vtempe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Chat
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
import com.vtempe.ui.navigation.Routes
import com.vtempe.ui.navigation.nutritionDetail
import com.vtempe.ui.screens.*
import com.vtempe.ui.theme.VTempeTheme
import com.vtempe.ui.*
import com.vtempe.ui.navigation.Routes.bottomNavRoutes
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// Глобальные провайдеры для высот баров, чтобы использовать в экранах
val LocalTopBarHeight = compositionLocalOf { 0.dp }
val LocalBottomBarHeight = compositionLocalOf { 0.dp }

@Composable
fun AppRoot() {
    VTempeTheme {
        var currentRoute by remember { mutableStateOf(Routes.Splash) }
        val tabRoutes = bottomDestinations.map { it.route }
        val isTabRoute = currentRoute in tabRoutes
        val showTopBar = currentRoute != Routes.Onboarding && currentRoute != Routes.Splash

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
                            currentRoute = currentRoute,
                            onNavigate = { dest -> currentRoute = dest }
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
                            currentRoute = currentRoute,
                            onNavigate = { currentRoute = it }
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
                                selectedRoute = currentRoute,
                                onRouteSelected = { currentRoute = it }
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
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    when (currentRoute) {
        Routes.Splash -> SplashScreen(onReady = onNavigate)
        Routes.Onboarding -> OnboardingScreen(onDone = { onNavigate(Routes.Home) })
        Routes.Home -> HomeScreen(onNavigate = onNavigate)
        Routes.Workout -> WorkoutScreen()
        Routes.Nutrition -> NutritionScreen(onOpenMeal = { day, index ->
            onNavigate(Routes.nutritionDetail(day, index))
        })
        Routes.Sleep -> SleepScreen()
        Routes.Progress -> ProgressScreen()
        Routes.Paywall -> PaywallScreen()
        Routes.Settings -> SettingsScreen(onEditProfile = { onNavigate(Routes.EditProfile) })
        Routes.EditProfile -> EditProfileScreen(onDone = { onNavigate(Routes.Settings) })
        Routes.Chat -> ChatScreen()
        Routes.ShoppingList -> ShoppingListScreen(onBack = { onNavigate(Routes.Nutrition) })
        else -> {
            if (currentRoute.startsWith("nutrition_detail")) {
                val segments = currentRoute.split("/")
                val day = segments.getOrNull(1) ?: "Mon"
                val idx = segments.getOrNull(2)?.toIntOrNull() ?: 0
                NutritionDetailScreen(day = day, index = idx, onBack = { onNavigate(Routes.Nutrition) })
            } else {
                HomeScreen(onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun BottomTabs(
    selectedRoute: String,
    onRouteSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        bottomDestinations.forEach { destination ->
            val isSelected = destination.route == selectedRoute
            val label = stringResource(destination.labelRes)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onRouteSelected(destination.route) }
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
    currentRoute: String,
    onNavigate: (String) -> Unit
) {
    val currentTitle = when (currentRoute) {
        Routes.Home -> stringResource(Res.string.app_name)
        Routes.Settings -> stringResource(Res.string.settings_title)
        Routes.Nutrition -> stringResource(Res.string.nav_nutrition)
        Routes.Chat -> stringResource(Res.string.chat_title)
        Routes.Workout -> stringResource(Res.string.nav_workout)
        Routes.Sleep -> stringResource(Res.string.nav_sleep)
        Routes.Paywall -> stringResource(Res.string.paywall_title)
        Routes.Progress -> stringResource(Res.string.nav_progress)
        Routes.EditProfile -> stringResource(Res.string.edit_profile_title)
        Routes.ShoppingList -> stringResource(Res.string.nutrition_tab_shopping)
        else -> {
            if (currentRoute.startsWith("nutrition_detail")) {
                stringResource(Res.string.nutrition_detail_title)
            } else {
                stringResource(Res.string.app_name)
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarIcon(Icons.Default.Chat, onClick = { onNavigate(Routes.Chat) })

        if (currentRoute !in bottomNavRoutes) {
            TopBarIcon(Icons.Default.ArrowBack, onClick = { onNavigate(Routes.Home) })
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

        TopBarIcon(Icons.Default.Star, onClick = { onNavigate(Routes.Paywall) })
        TopBarIcon(Icons.Default.Settings, onClick = { onNavigate(Routes.Settings) })
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
    val route: String,
    val labelRes: StringResource,
    val icon: ImageVector
)

private val bottomDestinations = listOf(
    BottomDestination(Routes.Home, Res.string.nav_home, AiIcons.Dashboard),
    BottomDestination(Routes.Workout, Res.string.nav_workout, AiIcons.Strength),
    BottomDestination(Routes.Nutrition, Res.string.nav_nutrition, AiIcons.Nutrition),
    BottomDestination(Routes.Sleep, Res.string.nav_sleep, AiIcons.Sleep),
    BottomDestination(Routes.Progress, Res.string.nav_progress, AiIcons.Progress)
)



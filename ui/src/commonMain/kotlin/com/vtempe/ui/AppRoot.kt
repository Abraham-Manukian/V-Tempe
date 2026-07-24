package com.vtempe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.icons.AiIcons
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.LanguagePreferences
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

val LocalAppLocaleUpdater = compositionLocalOf<(String?) -> Unit> { {} }

@Composable
fun AppRoot() {
    val savedTag = remember {
        KoinProvider.koin?.get<LanguagePreferences>()?.getLanguageTag()
    }
    var languageTag by remember { mutableStateOf(savedTag) }

    key(languageTag) {
    CompositionLocalProvider(LocalAppLocaleUpdater provides { languageTag = it }) {
    VTempeTheme {
        // Simple back stack — bottom-nav tabs replace each other, other screens push/pop
        val backStack = remember { mutableStateListOf<Destination>(Destination.Splash) }
        val currentDest = backStack.lastOrNull() ?: Destination.Splash

        fun navigateTo(dest: Destination) {
            if (dest.isBottomNav) {
                // Tab switch: clear everything above the bottom nav entry (or start fresh)
                val existingIdx = backStack.indexOfLast { it == dest }
                if (existingIdx >= 0) {
                    while (backStack.size > existingIdx + 1) backStack.removeAt(backStack.lastIndex)
                } else {
                    // Remove any other bottom-nav entries to avoid duplicates
                    backStack.removeAll { it.isBottomNav }
                    backStack.add(dest)
                }
            } else {
                backStack.add(dest)
            }
        }

        fun navigateBack() {
            // Not `.removeLast()` — on some devices (confirmed on an Android 9 BlueStacks
            // instance) it resolves to a NoSuchMethodError against SnapshotStateList at
            // runtime despite compiling fine. `.removeAt(lastIndex)` is unambiguous.
            if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        }

        // Handle system back button — prevents app exit when back stack has history
        BackHandlerCompat(enabled = backStack.size > 1) {
            navigateBack()
        }

        var pendingChatPrompt by remember { mutableStateOf<String?>(null) }
        var isActiveWorkout by remember { mutableStateOf(false) }
        val isTabRoute = currentDest.isBottomNav
        val showTopBar = currentDest !is Destination.Onboarding &&
                currentDest !is Destination.Splash && currentDest !is Destination.Welcome && !isActiveWorkout

        LaunchedEffect(currentDest) {
            if (currentDest !is Destination.Workout) isActiveWorkout = false
        }

        val focusManager = LocalFocusManager.current
        val density = LocalDensity.current
        var topBarHeight by remember { mutableStateOf(0.dp) }
        var bottomBarHeight by remember { mutableStateOf(0.dp) }

        // Пробрасываем высоты баров вниз по дереву
        CompositionLocalProvider(
            LocalTopBarHeight provides topBarHeight,
            LocalBottomBarHeight provides bottomBarHeight
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } }
            ) {
                AppBackground()

                Scaffold(
                    containerColor = Color.Transparent,
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    // Cap all screen content to a phone-column width and center it, so on tablets /
                    // wide screens (720dp+) every screen renders as one tidy centered column instead
                    // of spreading into sparse multi-column layouts with big empty margins. On real
                    // phones (≤600dp) this is a no-op. The gradient background and the top/bottom
                    // bars are drawn outside this Box, so they still span the full width.
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        Box(modifier = Modifier.widthIn(max = 600.dp).fillMaxSize()) {
                            AppNavigationHost(
                                current = currentDest,
                                pendingChatPrompt = pendingChatPrompt,
                                onPromptConsumed = { pendingChatPrompt = null },
                                onNavigate = { dest -> navigateTo(dest) },
                                onAskCoach = { prompt ->
                                    pendingChatPrompt = prompt
                                    navigateTo(Destination.Chat)
                                },
                                onEnterActiveWorkout = { isActiveWorkout = true },
                                onExitActiveWorkout = { isActiveWorkout = false }
                            )
                        }
                    }
                }

                // Top Bar
                if (showTopBar) {
                    GlassTopBarContainer(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .widthIn(max = 600.dp)
                            .fillMaxWidth()
                            .onGloballyPositioned { coords ->
                                topBarHeight = with(density) { coords.size.height.toDp() }
                            }
                    ) {
                        TopBar(
                            current = currentDest,
                            canGoBack = backStack.size > 1 && !currentDest.isBottomNav,
                            onBack = { navigateBack() },
                            onNavigate = { navigateTo(it) }
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
                                onSelect = { navigateTo(it) }
                            )
                        }
                    }
                }
            }
        }
    }
    } // CompositionLocalProvider
    } // key(languageTag)
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
        is Destination.Welcome -> AuthScreen(
            onAuthenticated = { onNavigate(Destination.Onboarding) },
            onSkip = { onNavigate(Destination.Onboarding) }
        )
        is Destination.Onboarding -> OnboardingScreen(onDone = { onNavigate(Destination.Home) })
        is Destination.Home -> HomeScreen(onNavigate = onNavigate)
        is Destination.Workout -> WorkoutScreen(
            onAskCoach = onAskCoach,
            onNavigateToLibrary = { onNavigate(Destination.ExerciseLibrary) },
            onEnterActiveWorkout = { onEnterActiveWorkout() },
            onExitActiveWorkout = { onExitActiveWorkout() }
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
        is Destination.Settings -> SettingsScreen(
            onEditProfile = { onNavigate(Destination.EditProfile) },
            onAccount = { onNavigate(Destination.Auth) }
        )
        is Destination.EditProfile -> EditProfileScreen(onDone = { onNavigate(Destination.Settings) })
        is Destination.Auth -> AuthScreen()
        is Destination.Chat -> ChatScreen(
            initialPrompt = pendingChatPrompt,
            onPromptConsumed = onPromptConsumed,
            onNavigate = onNavigate
        )
        is Destination.ShoppingList -> ShoppingListScreen(onBack = { onNavigate(Destination.Nutrition) })
        is Destination.ExerciseLibrary -> ExerciseLibraryScreen()
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
                TopBarIcon(Icons.AutoMirrored.Default.ArrowBack, onClick = onBack)
            } else {
                TopBarIcon(Icons.AutoMirrored.Default.Chat, onClick = { onNavigate(Destination.Chat) })
            }

            Row {
                if (current.isBottomNav) {
                    TopBarIcon(Icons.Default.Star, onClick = { onNavigate(Destination.Paywall) })
                }
                TopBarIcon(Icons.Default.Settings, onClick = { onNavigate(Destination.Settings) })
            }
        }
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



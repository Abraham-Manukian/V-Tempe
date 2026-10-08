package com.vtempe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.vtempe.ui.chrome.BottomTabs
import com.vtempe.ui.chrome.TopBar
import com.vtempe.ui.navigation.AppNavigationHost
import com.vtempe.ui.navigation.Destination
import com.vtempe.ui.navigation.backStackAfterNavigation
import com.vtempe.ui.navigation.isBottomNav

@Composable
internal fun AppShell(backStack: List<Destination>, onBackStackChange: (List<Destination>) -> Unit) {
    // Simple back stack — bottom-nav tabs replace each other, other screens push/pop
    val currentDest = backStack.lastOrNull() ?: Destination.Splash

    fun navigateTo(dest: Destination) {
        onBackStackChange(backStackAfterNavigation(backStack, dest))
    }

    fun navigateBack() {
        if (backStack.size > 1) onBackStackChange(backStack.dropLast(1))
    }

    // Handle system back button — prevents app exit when back stack has history
    BackHandlerCompat(enabled = backStack.size > 1) {
        navigateBack()
    }

    var pendingChatPrompt by remember { mutableStateOf<String?>(null) }
    var isActiveWorkout by remember { mutableStateOf(false) }
    val isTabRoute = currentDest.isBottomNav
    val showTopBar = currentDest !is Destination.Onboarding && currentDest !is Destination.HealthConsent &&
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
        LocalTopBarHeight provides if (showTopBar) topBarHeight else 0.dp,
        LocalBottomBarHeight provides if (isTabRoute) bottomBarHeight else 0.dp
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

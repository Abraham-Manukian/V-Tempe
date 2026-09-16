package com.vtempe.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.vtempe.ui.*
import com.vtempe.ui.navigation.Destination
import com.vtempe.ui.screens.*

@Composable
internal fun AppNavigationHost(
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

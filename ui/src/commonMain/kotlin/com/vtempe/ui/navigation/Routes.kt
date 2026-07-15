package com.vtempe.ui.navigation

sealed class Destination {
    object Splash : Destination()
    object Onboarding : Destination()
    object Home : Destination()
    object Workout : Destination()
    object Nutrition : Destination()
    object Sleep : Destination()
    object Progress : Destination()
    object Paywall : Destination()
    object Settings : Destination()
    object EditProfile : Destination()
    object Auth : Destination()
    object Chat : Destination()
    object ShoppingList : Destination()
    object ExerciseLibrary : Destination()
    data class NutritionDetail(val day: String, val index: Int) : Destination()
}

val Destination.isBottomNav: Boolean
    get() = this is Destination.Home ||
            this is Destination.Workout ||
            this is Destination.Nutrition ||
            this is Destination.Sleep ||
            this is Destination.Progress


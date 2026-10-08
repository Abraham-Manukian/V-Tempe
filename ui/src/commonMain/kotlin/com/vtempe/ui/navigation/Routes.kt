package com.vtempe.ui.navigation

@kotlinx.serialization.Serializable
sealed class Destination {
    @kotlinx.serialization.Serializable
    object Splash : Destination()
    /** Shown once, before onboarding, to first-time users only — social sign-in (Google on
     *  Android, Apple on iOS) or skip. Not shown again once a profile exists. */
    @kotlinx.serialization.Serializable
    object Welcome : Destination()
    @kotlinx.serialization.Serializable
    object Onboarding : Destination()
    /** Shown once at launch to users with a profile but no decision on the current
     *  health-data consent version (e.g. onboarded before the consent existed). */
    @kotlinx.serialization.Serializable
    object HealthConsent : Destination()
    @kotlinx.serialization.Serializable
    object Home : Destination()
    @kotlinx.serialization.Serializable
    object Workout : Destination()
    @kotlinx.serialization.Serializable
    object Nutrition : Destination()
    @kotlinx.serialization.Serializable
    object Sleep : Destination()
    @kotlinx.serialization.Serializable
    object Progress : Destination()
    @kotlinx.serialization.Serializable
    object Paywall : Destination()
    @kotlinx.serialization.Serializable
    object Settings : Destination()
    @kotlinx.serialization.Serializable
    object EditProfile : Destination()
    @kotlinx.serialization.Serializable
    object Auth : Destination()
    @kotlinx.serialization.Serializable
    object Chat : Destination()
    @kotlinx.serialization.Serializable
    object ShoppingList : Destination()
    @kotlinx.serialization.Serializable
    object ExerciseLibrary : Destination()
    @kotlinx.serialization.Serializable
    data class NutritionDetail(val day: String, val index: Int) : Destination()
}

val Destination.isBottomNav: Boolean
    get() = this is Destination.Home ||
            this is Destination.Workout ||
            this is Destination.Nutrition ||
            this is Destination.Sleep ||
            this is Destination.Progress


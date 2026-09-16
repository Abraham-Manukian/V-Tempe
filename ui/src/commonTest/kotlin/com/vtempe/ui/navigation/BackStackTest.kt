package com.vtempe.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals

class BackStackTest {
    @Test
    fun firstLaunchDoesNotLeaveSplashBehindWelcome() {
        assertEquals(listOf(Destination.Welcome), navigate(Destination.Splash, Destination.Welcome))
    }

    @Test
    fun existingProfileStartsAtHomeWithoutSplashHistory() {
        assertEquals(listOf(Destination.Home), navigate(Destination.Splash, Destination.Home))
    }

    @Test
    fun completedOnboardingCannotBeReachedFromHomeByBack() {
        assertEquals(
            listOf(Destination.Home),
            navigate(Destination.Splash, Destination.Welcome, Destination.Onboarding, Destination.Home)
        )
    }

    @Test
    fun switchingTabsDiscardsPreviousDetails() {
        assertEquals(
            listOf(Destination.Workout),
            navigate(Destination.Nutrition, Destination.NutritionDetail("Mon", 0), Destination.Workout)
        )
    }

    @Test
    fun savingProfileReturnsToExistingSettingsWithoutAnEditLoop() {
        assertEquals(
            listOf(Destination.Home, Destination.Settings),
            navigate(Destination.Home, Destination.Settings, Destination.EditProfile, Destination.Settings)
        )
    }

    @Test
    fun repeatedRouteDoesNotCreateDuplicates() {
        assertEquals(
            listOf(Destination.Home, Destination.Chat),
            navigate(Destination.Home, Destination.Chat, Destination.Chat)
        )
    }

    @Test
    fun openingDetailsPreservesTheParentForBack() {
        val detail = Destination.NutritionDetail("Tue", 1)
        assertEquals(listOf(Destination.Nutrition, detail), navigate(Destination.Nutrition, detail))
    }

    private fun navigate(vararg destinations: Destination): List<Destination> =
        destinations.fold(emptyList()) { stack, destination -> backStackAfterNavigation(stack, destination) }
}

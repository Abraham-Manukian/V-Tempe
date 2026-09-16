package com.vtempe.ui.navigation

internal fun backStackAfterNavigation(
    current: List<Destination>,
    destination: Destination
): List<Destination> {
    // Splash is transient; a tab starts a new root after onboarding or a tab switch.
    if (current.lastOrNull() == Destination.Splash || destination.isBottomNav) {
        return listOf(destination)
    }
    val existingIndex = current.indexOfLast { it == destination }
    return if (existingIndex >= 0) current.take(existingIndex + 1) else current + destination
}

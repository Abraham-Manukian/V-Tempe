package com.vtempe.ui.navigation

import androidx.compose.runtime.saveable.SaverScope
import com.vtempe.ui.NavigationSaver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NavigationPersistenceTest {
    private val scope = SaverScope { true }

    @Test
    fun skippedAuthenticationAndOnboardingSurviveStateRestoration() {
        val stack = listOf(Destination.Welcome, Destination.Onboarding)
        val saved = with(NavigationSaver) { scope.save(stack) }
        assertEquals(stack, NavigationSaver.restore(assertNotNull(saved)))
    }
}

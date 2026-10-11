package com.vtempe.ui.screens

import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.ui.navigation.Destination
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** VT-AUD-077: only a profile that was read and is absent leads to first-run onboarding. */
class StartDecisionTest {
    private val stored = Profile("p", 30, Sex.MALE, 180, 80.0, Goal.MAINTAIN, 2)

    private class Profiles(var current: Profile?, var failure: Exception? = null) : ProfileRepository {
        override suspend fun getProfile(): Profile? { failure?.let { throw it }; return current }
        override suspend fun upsertProfile(profile: Profile) = Unit
        override suspend fun clearAll() = Unit
    }

    private fun deps(profiles: () -> ProfileRepository) =
        StartupDependencies(accountSession = { null }, profiles = profiles, consent = { null })

    @Test
    fun aReadProfileOrItsAbsenceDecidesTheStart() = runTest {
        assertEquals(StartDecision.Go(Destination.Welcome), decideStart(deps { Profiles(null) }))
        assertEquals(StartDecision.Go(Destination.Home), decideStart(deps { Profiles(stored) }))
    }

    @Test
    fun aReadOrOpenFailureIsNotFirstRunAndRetryRecovers() = runTest {
        val profiles = Profiles(stored, failure = IllegalStateException("database is locked"))
        assertEquals(StartDecision.StorageFailed, decideStart(deps { profiles }))
        assertEquals(StartDecision.StorageFailed, decideStart(deps { error("driver failed to open") }))
        assertEquals(StartDecision.StorageFailed, decideStart(null), "DI not started")

        profiles.failure = null // transient: the retry reads the stored profile
        assertEquals(StartDecision.Go(Destination.Home), decideStart(deps { profiles }))
    }

    @Test
    fun cancellationIsNotAMissingProfile() = runTest {
        assertFailsWith<CancellationException> {
            decideStart(deps { Profiles(null, failure = CancellationException("left the screen")) })
        }
    }
}

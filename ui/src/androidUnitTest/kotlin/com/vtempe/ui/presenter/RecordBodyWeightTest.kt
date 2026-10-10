package com.vtempe.ui.presenter

import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.domain.model.Goal
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.model.Sex
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.RecordBodyWeight
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The weekly weigh-in updates the weight every plan is computed from. */
class RecordBodyWeightTest {
    private class Profiles(var current: Profile?) : ProfileRepository {
        var writes = 0
        override suspend fun getProfile() = current
        override suspend fun upsertProfile(profile: Profile) { writes++; current = profile }
        override suspend fun clearAll() { current = null }
    }

    private val weights = WeightStore(memorySettings())
    private val profiles = Profiles(Profile("p", 30, Sex.MALE, 180, 80.0, Goal.LOSE_FAT, 2))
    private val record = RecordBodyWeight(weights, profiles) { LocalDate(2026, 10, 10) }

    @Test
    fun aWeighInBecomesTheProfileWeight() = runTest {
        record(76.5)
        assertEquals(76.5, weights.latestWeight())
        assertEquals(76.5, profiles.current?.weightKg)

        record(76.5)
        assertEquals(1, profiles.writes, "an unchanged weight is not rewritten")
    }

    @Test
    fun anImpossibleWeightNeverReachesTheProfile() = runTest {
        record(450.0) // the history accepts it, the plans' profile bounds do not
        assertEquals(80.0, profiles.current?.weightKg)
        assertEquals(0, profiles.writes)
    }
}

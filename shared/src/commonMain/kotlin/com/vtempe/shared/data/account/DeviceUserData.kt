package com.vtempe.shared.data.account

import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.data.repo.WorkoutProgressStore
import com.vtempe.shared.domain.account.LocalUserData
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.ExerciseCalibrationRepository
import com.vtempe.shared.domain.repository.ProfileRepository

/**
 * Every store holding a person's data on this device. A new per-user store must be added here,
 * otherwise it survives account switches.
 */
class DeviceUserData(
    private val profiles: ProfileRepository,
    private val coachCache: CoachCacheRepository,
    private val workoutProgress: WorkoutProgressStore,
    private val sleep: SleepStore,
    private val weight: WeightStore,
    private val chatHistory: ChatHistoryStore,
    private val calibrations: ExerciseCalibrationRepository
) : LocalUserData {

    override suspend fun hasUserData(): Boolean =
        profiles.getProfile() != null ||
            workoutProgress.current().isNotEmpty() ||
            sleep.recentEntries(limit = 1).isNotEmpty() ||
            weight.latestWeight() != null ||
            chatHistory.load().isNotEmpty() ||
            calibrations.list().isNotEmpty()

    override suspend fun clearUserData() {
        profiles.clearAll()
        coachCache.clearAllAndResetEpoch()
        workoutProgress.clear()
        sleep.clear()
        weight.clear()
        chatHistory.clear()
        calibrations.clearAll()
    }
}

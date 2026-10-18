package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.model.ProfileBounds
import com.vtempe.shared.domain.repository.BodyWeightLog
import com.vtempe.shared.domain.repository.ProfileRepository
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * The weekly weigh-in. Besides the weight history it updates the profile's weight, which every
 * plan is computed from (calories, protein, loads) — on the server and offline alike — so the
 * next plan follows the user's current weight instead of the weight given at registration.
 */
class RecordBodyWeight(
    private val weightLog: BodyWeightLog,
    private val profileRepository: ProfileRepository,
    private val today: () -> LocalDate = { Clock.System.todayIn(TimeZone.currentSystemDefault()) },
) {
    suspend operator fun invoke(kg: Double) {
        weightLog.logWeight(today().toString(), kg)
        if (!kg.isFinite() || kg !in ProfileBounds.WEIGHT_KG) return
        val profile = profileRepository.getProfile() ?: return
        if (profile.weightKg != kg) profileRepository.upsertProfile(profile.copy(weightKg = kg))
    }
}

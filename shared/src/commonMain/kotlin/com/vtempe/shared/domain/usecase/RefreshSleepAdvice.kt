package com.vtempe.shared.domain.usecase

import com.vtempe.shared.domain.model.Advice
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.AiTrainerRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.util.DataResult

/**
 * Asks the coach for fresh sleep tips and stores a fresh answer as THE sleep advice
 * (VT-AUD-041), which every screen observes. A cached answer served while offline is returned
 * (marked `fromCache`) but not stored over newer advice.
 */
class RefreshSleepAdvice(
    private val profileRepository: ProfileRepository,
    private val aiTrainerRepository: AiTrainerRepository,
    private val adviceRepository: AdviceRepository,
) {
    suspend operator fun invoke(): DataResult<Advice> {
        val profile = profileRepository.getProfile()
            ?: return DataResult.Failure(DataResult.Reason.CacheMissing, "no profile")
        val result = aiTrainerRepository.getSleepAdvice(profile)
        if (result is DataResult.Success && !result.fromCache && result.data.messages.isNotEmpty()) {
            adviceRepository.saveAdvice(TOPIC, result.data)
        }
        return result
    }

    companion object {
        const val TOPIC = "sleep"
    }
}

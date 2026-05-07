package com.vtempe.ui.screens

import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Shared business logic for the sleep screen.
 * Used by [SleepViewModel] (Android) and [IosSleepPresenter] (iOS).
 * Each platform supplies its own [CoroutineScope].
 */
class SleepPresenterDelegate(
    private val adviceRepository: AdviceRepository,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(SleepState())
    val state: StateFlow<SleepState> = _state.asStateFlow()

    init {
        scope.launch {
            adviceRepository.observeAdvice("sleep").collect { advice ->
                _state.value = _state.value.copy(
                    tips = advice.messages,
                    disclaimer = advice.disclaimer
                )
            }
        }
        fetchAdvice()
    }

    fun sync() {
        fetchAdvice()
    }

    private fun fetchAdvice() {
        scope.launch {
            val profile = profileRepository.getProfile() ?: return@launch
            _state.value = _state.value.copy(syncing = true)
            runCatching { adviceRepository.getAdvice(profile, mapOf("topic" to "sleep")) }
                .onFailure { /* observeAdvice stream keeps stale data visible */ }
            _state.value = _state.value.copy(syncing = false)
        }
    }
}

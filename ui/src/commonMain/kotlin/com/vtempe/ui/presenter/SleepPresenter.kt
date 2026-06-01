package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SleepState(
    val tips: List<String> = emptyList(),
    val weeklyHours: List<Int> = emptyList(),
    val syncing: Boolean = false,
    val disclaimer: String? = "Not medical advice"
)

interface SleepPresenter {
    val state: StateFlow<SleepState>
    fun sync()
}

class SleepPresenterDelegate(
    private val adviceRepository: AdviceRepository,
    private val profileRepository: ProfileRepository,
    private val scope: CoroutineScope,
) : SleepPresenter {

    private val _state = MutableStateFlow(SleepState())
    override val state: StateFlow<SleepState> = _state.asStateFlow()

    init {
        adviceRepository.observeAdvice("sleep")
            .onEach { advice ->
                _state.update { it.copy(tips = advice.messages, disclaimer = advice.disclaimer) }
            }
            .catch { Napier.e("SleepPresenter observe error", it) }
            .launchIn(scope)
    }

    override fun sync() {
        _state.update { it.copy(syncing = true) }
        scope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            if (profile != null) {
                runCatching {
                    val advice = adviceRepository.getAdvice(profile, mapOf("topic" to "sleep"))
                    adviceRepository.saveAdvice("sleep", advice)
                }.onFailure { Napier.e("Sleep sync failed", it) }
            }
            _state.update { it.copy(syncing = false) }
        }
    }
}

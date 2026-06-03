package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.russhwolf.settings.Settings
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

private const val KEY_SLEEP_TODAY_MINUTES = "sleep_today_minutes"

@Immutable
data class SleepState(
    val tips: List<String> = emptyList(),
    val weeklyHours: List<Int> = emptyList(),
    val syncing: Boolean = false,
    val disclaimer: String? = "Not medical advice",
    /** Minutes logged by the user for today's sleep */
    val loggedMinutes: Int = 0,
    val logSaved: Boolean = false,
)

interface SleepPresenter {
    val state: StateFlow<SleepState>
    fun sync()
    fun logSleep(hours: Int, minutes: Int)
}

class SleepPresenterDelegate(
    private val adviceRepository: AdviceRepository,
    private val profileRepository: ProfileRepository,
    private val settings: Settings,
    private val scope: CoroutineScope,
) : SleepPresenter {

    private val _state = MutableStateFlow(
        SleepState(loggedMinutes = settings.getIntOrNull(KEY_SLEEP_TODAY_MINUTES) ?: 0)
    )
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

    override fun logSleep(hours: Int, minutes: Int) {
        val total = (hours * 60 + minutes).coerceIn(0, 24 * 60)
        settings.putInt(KEY_SLEEP_TODAY_MINUTES, total)
        _state.update { it.copy(loggedMinutes = total, logSaved = true) }
        scope.launch {
            kotlinx.coroutines.delay(2000)
            _state.update { it.copy(logSaved = false) }
        }
    }
}

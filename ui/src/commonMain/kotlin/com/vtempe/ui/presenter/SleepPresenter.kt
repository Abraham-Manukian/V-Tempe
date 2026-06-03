package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

@Immutable
data class SleepState(
    val tips: List<String> = emptyList(),
    /** Hours slept per day for Mon..Sun of the current week (0 if not logged). */
    val weeklyHours: List<Int> = emptyList(),
    val syncing: Boolean = false,
    val disclaimer: String? = "Not medical advice",
    /** Minutes logged by the user for tonight's sleep */
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
    private val sleepStore: SleepStore,
    private val scope: CoroutineScope,
) : SleepPresenter {

    private val _state = MutableStateFlow(SleepState())
    override val state: StateFlow<SleepState> = _state.asStateFlow()

    init {
        // Load persisted sleep data immediately
        refreshFromStore()

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
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        sleepStore.logSleep(today.toString(), total)
        refreshFromStore()
        _state.update { it.copy(logSaved = true) }
        scope.launch {
            delay(2000)
            _state.update { it.copy(logSaved = false) }
        }
    }

    private fun refreshFromStore() {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val todayStr = today.toString()
        val loggedMinutes = sleepStore.getForDate(todayStr)

        // Build weeklyHours: Mon..Sun of current week, in hours
        val daysFromMonday = today.dayOfWeek.ordinal // Mon=0, Sun=6
        val monday = today.minus(daysFromMonday, DateTimeUnit.DAY)
        val weeklyHours = (0..6).map { offset ->
            val day = monday.plus(offset, DateTimeUnit.DAY)
            sleepStore.getForDate(day.toString()) / 60
        }

        _state.update { it.copy(loggedMinutes = loggedMinutes, weeklyHours = weeklyHours) }
    }
}

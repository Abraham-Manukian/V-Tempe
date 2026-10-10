package com.vtempe.ui.presenter

import androidx.compose.runtime.Immutable
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.domain.model.SleepBounds
import com.vtempe.shared.domain.repository.AdviceRepository
import com.vtempe.shared.domain.usecase.RefreshSleepAdvice
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** Where the last request for new sleep tips stands (VT-AUD-043). */
enum class SleepAdviceStatus { IDLE, LOADING, OFFLINE, FAILED }

@Immutable
data class SleepState(
    val tips: List<String> = emptyList(),
    val disclaimer: String? = null,
    val adviceStatus: SleepAdviceStatus = SleepAdviceStatus.IDLE,
    /** The local day the diary shows and a save writes to; null until first read. */
    val date: LocalDate? = null,
    /** Minutes per day Mon..Sun of [date]'s week; null where nothing was logged. */
    val weeklyMinutes: List<Int?> = List(7) { null },
    /** Minutes logged for [date], or null when that day has no entry yet. */
    val loggedMinutes: Int? = null,
    val loggedNotes: String = "",
    val logSaved: Boolean = false,
)

interface SleepPresenter {
    val state: StateFlow<SleepState>
    /** Asks the coach for new tips; ignored while a request is already running. */
    fun refreshAdvice()
    /**
     * Saves [date]'s entry. [minutes] null keeps the stored duration (a note-only edit, VT-AUD-047);
     * a day without an entry then gets 0.
     */
    fun logSleep(date: LocalDate, minutes: Int?, notes: String)
}

class SleepPresenterDelegate(
    private val refreshSleepAdvice: RefreshSleepAdvice,
    adviceRepository: AdviceRepository,
    private val sleepStore: SleepStore,
    private val scope: CoroutineScope,
    dates: Flow<LocalDate> = localDates(),
) : SleepPresenter {

    private val _state = MutableStateFlow(SleepState())
    override val state: StateFlow<SleepState> = _state.asStateFlow()
    private var refreshJob: Job? = null
    private var savedBannerJob: Job? = null

    init {
        // Tips come from the one stored advice, whoever wrote it — bootstrap, chat or a refresh.
        adviceRepository.observeAdvice(RefreshSleepAdvice.TOPIC)
            .onEach { advice -> _state.update { it.copy(tips = advice.messages, disclaimer = advice.disclaimer.ifBlank { null }) } }
            .launchIn(scope)
        // The diary follows the store (restores, other screens) and the calendar day.
        combine(dates, sleepStore.revision) { day, _ -> day }
            .onEach(::showDiary)
            .launchIn(scope)
    }

    override fun refreshAdvice() {
        if (refreshJob?.isActive == true) return // one request at a time (VT-AUD-042)
        _state.update { it.copy(adviceStatus = SleepAdviceStatus.LOADING) }
        refreshJob = scope.launch {
            val status = try {
                when (val result = refreshSleepAdvice()) {
                    is DataResult.Success -> if (result.fromCache) SleepAdviceStatus.OFFLINE else SleepAdviceStatus.IDLE
                    is DataResult.Failure -> when (result.reason) {
                        DataResult.Reason.Network, DataResult.Reason.Timeout -> SleepAdviceStatus.OFFLINE
                        else -> SleepAdviceStatus.FAILED
                    }
                }
            } catch (e: CancellationException) {
                _state.update { it.copy(adviceStatus = SleepAdviceStatus.IDLE) }
                throw e
            } catch (e: Exception) {
                Napier.e("Sleep advice refresh failed", e)
                SleepAdviceStatus.FAILED
            }
            _state.update { it.copy(adviceStatus = status) }
        }
    }

    override fun logSleep(date: LocalDate, minutes: Int?, notes: String) {
        val total = (minutes ?: sleepStore.entryFor(date) ?: 0).coerceIn(SleepBounds.MINUTES)
        sleepStore.logSleep(date.toString(), total, notes) // the store's revision refreshes the diary
        _state.update { it.copy(logSaved = true) }
        savedBannerJob?.cancel()
        savedBannerJob = scope.launch {
            delay(2000)
            _state.update { it.copy(logSaved = false) }
        }
    }

    private fun showDiary(day: LocalDate) {
        _state.update {
            it.copy(
                date = day,
                loggedMinutes = sleepStore.entryFor(day),
                loggedNotes = sleepStore.getNotesForDate(day.toString()),
                weeklyMinutes = sleepWeekMinutes(day, sleepStore::entryFor),
            )
        }
    }
}

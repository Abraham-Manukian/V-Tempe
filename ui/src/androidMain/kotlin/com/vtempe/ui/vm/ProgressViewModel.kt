package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.ui.presenter.ProgressPresenter
import com.vtempe.ui.presenter.ProgressPresenterDelegate
import com.vtempe.ui.presenter.ProgressState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.LocalDate

class ProgressViewModel(
    trainingRepository: TrainingRepository,
    nutritionRepository: NutritionRepository
) : ViewModel(), ProgressPresenter {

    private val delegate = ProgressPresenterDelegate(
        trainingRepository = trainingRepository,
        nutritionRepository = nutritionRepository,
        scope = viewModelScope
    )

    override val state: StateFlow<ProgressState> get() = delegate.state
    override fun selectDate(date: LocalDate) = delegate.selectDate(date)
    override fun clearDate() = delegate.clearDate()
    override fun prevMonth() = delegate.prevMonth()
    override fun nextMonth() = delegate.nextMonth()
}

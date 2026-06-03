package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.data.repo.SleepStore
import com.vtempe.shared.data.repo.WeightStore
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.presenter.HomePresenter
import com.vtempe.ui.presenter.HomePresenterDelegate
import com.vtempe.ui.presenter.HomeState
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    trainingRepository: TrainingRepository,
    nutritionRepository: NutritionRepository,
    ensureCoachData: EnsureCoachData,
    sleepStore: SleepStore,
    weightStore: WeightStore,
) : ViewModel(), HomePresenter {

    private val delegate = HomePresenterDelegate(
        trainingRepository = trainingRepository,
        nutritionRepository = nutritionRepository,
        ensureCoachData = ensureCoachData,
        sleepStore = sleepStore,
        weightStore = weightStore,
        scope = viewModelScope
    )

    override val state: StateFlow<HomeState> = delegate.state
    override fun logWeight(kg: Double) = delegate.logWeight(kg)
    override fun dismissWeightCheckin() = delegate.dismissWeightCheckin()
}

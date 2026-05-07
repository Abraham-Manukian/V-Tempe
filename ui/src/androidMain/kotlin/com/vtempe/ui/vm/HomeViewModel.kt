package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.screens.HomePresenter
import com.vtempe.ui.screens.HomePresenterDelegate
import com.vtempe.ui.screens.HomeState
import kotlinx.coroutines.flow.StateFlow

class HomeViewModel(
    trainingRepository: TrainingRepository,
    ensureCoachData: EnsureCoachData,
) : ViewModel(), HomePresenter {

    private val delegate = HomePresenterDelegate(
        trainingRepository = trainingRepository,
        ensureCoachData = ensureCoachData,
        scope = viewModelScope
    )

    override val state: StateFlow<HomeState> = delegate.state
}

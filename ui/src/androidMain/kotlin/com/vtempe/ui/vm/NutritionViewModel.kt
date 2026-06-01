package com.vtempe.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.domain.repository.CoachCacheRepository
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.presenter.NutritionPresenter
import com.vtempe.ui.presenter.NutritionPresenterDelegate
import com.vtempe.ui.presenter.NutritionState
import kotlinx.coroutines.flow.StateFlow

class NutritionViewModel(
    ensureCoachData: EnsureCoachData,
    nutritionRepository: NutritionRepository,
    coachCache: CoachCacheRepository,
) : ViewModel(), NutritionPresenter {

    private val delegate = NutritionPresenterDelegate(
        ensureCoachData = ensureCoachData,
        nutritionRepository = nutritionRepository,
        coachCache = coachCache,
        scope = viewModelScope
    )

    override val state: StateFlow<NutritionState> get() = delegate.state
    override fun refresh(force: Boolean) = delegate.refresh(force)
    override fun selectDay(day: String) = delegate.selectDay(day)
}

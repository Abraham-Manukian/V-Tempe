package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.ui.presenter.NutritionPresenter
import com.vtempe.ui.presenter.NutritionPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

private class IosNutritionPresenter(ensureCoachData: EnsureCoachData, nutritionRepository: NutritionRepository) : NutritionPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = NutritionPresenterDelegate(ensureCoachData = ensureCoachData, nutritionRepository = nutritionRepository, scope = scope)
    override val state get() = delegate.state
    override fun refresh(weekIndex: Int, force: Boolean) = delegate.refresh(weekIndex, force)
    override fun selectDay(day: String) = delegate.selectDay(day)
    fun close() = job.cancel()
}

@Composable
actual fun rememberNutritionPresenter(): NutritionPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosNutritionPresenter(ensureCoachData = koin.get(), nutritionRepository = koin.get())
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.vtempe.shared.data.di.KoinProvider
import com.vtempe.shared.domain.repository.NutritionRepository
import com.vtempe.shared.domain.repository.TrainingRepository
import com.vtempe.ui.presenter.ProgressPresenter
import com.vtempe.ui.presenter.ProgressPresenterDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.LocalDate

private class IosProgressPresenter(trainingRepository: TrainingRepository, nutritionRepository: NutritionRepository) : ProgressPresenter {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val delegate = ProgressPresenterDelegate(trainingRepository = trainingRepository, nutritionRepository = nutritionRepository, scope = scope)
    override val state get() = delegate.state
    override fun selectDate(date: LocalDate) = delegate.selectDate(date)
    override fun clearDate() = delegate.clearDate()
    override fun prevMonth() = delegate.prevMonth()
    override fun nextMonth() = delegate.nextMonth()
    fun close() = job.cancel()
}

@Composable
actual fun rememberProgressPresenter(): ProgressPresenter {
    val presenter = remember {
        val koin = requireNotNull(KoinProvider.koin) { "Koin is not started" }
        IosProgressPresenter(trainingRepository = koin.get(), nutritionRepository = koin.get())
    }
    DisposableEffect(Unit) { onDispose { presenter.close() } }
    return presenter
}

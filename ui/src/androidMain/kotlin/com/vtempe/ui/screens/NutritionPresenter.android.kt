package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.NutritionPresenter
import com.vtempe.ui.vm.NutritionViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberNutritionPresenter(): NutritionPresenter =
    koinViewModel<NutritionViewModel>()


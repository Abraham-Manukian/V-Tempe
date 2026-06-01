package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.NutritionPresenter

@Composable
expect fun rememberNutritionPresenter(): NutritionPresenter

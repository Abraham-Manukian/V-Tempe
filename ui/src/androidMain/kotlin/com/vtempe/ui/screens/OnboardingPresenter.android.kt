package com.vtempe.ui.screens

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.OnboardingPresenter
import com.vtempe.ui.vm.OnboardingViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberOnboardingPresenter(): OnboardingPresenter =
    koinViewModel<OnboardingViewModel>()

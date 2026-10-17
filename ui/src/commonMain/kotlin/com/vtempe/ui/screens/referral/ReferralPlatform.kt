package com.vtempe.ui.screens.referral

import androidx.compose.runtime.Composable
import com.vtempe.ui.presenter.ReferralPresenter

@Composable
expect fun rememberReferralPresenter(): ReferralPresenter

/** Opens the platform share sheet with [text] (Android chooser, iOS activity sheet). */
@Composable
expect fun rememberShareText(): (text: String) -> Unit

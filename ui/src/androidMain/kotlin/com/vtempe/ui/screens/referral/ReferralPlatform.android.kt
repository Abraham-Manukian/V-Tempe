package com.vtempe.ui.screens.referral

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vtempe.ui.presenter.ReferralPresenter
import com.vtempe.ui.vm.ReferralViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
actual fun rememberReferralPresenter(): ReferralPresenter = koinViewModel<ReferralViewModel>()

@Composable
actual fun rememberShareText(): (text: String) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { text ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            // The chooser may be started from a non-Activity context (e.g. an Application wrapper).
            val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
    }
}

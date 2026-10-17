@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.referral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.ui.*
import com.vtempe.ui.presenter.ReferralPresenter
import org.jetbrains.compose.resources.stringResource

/**
 * Invite friends: the user's code (copy / share), the code they entered, and the balance their
 * invitations earned. Everything shown comes from the server; this screen only renders it.
 */
@Composable
fun ReferralScreen(
    onSignIn: () -> Unit,
    presenter: ReferralPresenter = rememberReferralPresenter(),
) {
    val state by presenter.state.collectAsState()
    LaunchedEffect(Unit) { presenter.refresh() }
    val overview = state.overview

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(start = 16.dp, end = 16.dp, top = LocalTopBarHeight.current + 16.dp,
                bottom = LocalBottomBarHeight.current + 24.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(Res.string.referral_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        when {
            state.signInRequired -> SignInCard(onSignIn)
            overview == null && state.loadFailed -> RetryCard(onRetry = presenter::refresh)
            overview == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            else -> {
                // A reload failed but older data is shown: say so instead of passing it off as fresh.
                if (state.loadFailed) RetryCard(onRetry = presenter::refresh)
                InviteCard(
                    overview = overview,
                    requestingCode = state.requestingCode,
                    subscriptionRequired = state.subscriptionRequired,
                    codeRequestFailed = state.codeRequestFailed,
                    onRequestCode = presenter::requestCode,
                )
                BalanceCard(overview)
                if (!overview.referred) {
                    EnterCodeCard(
                        input = state.codeInput,
                        attaching = state.attaching,
                        result = state.attachResult,
                        onInput = presenter::onCodeInput,
                        onApply = presenter::attach,
                    )
                } else {
                    InfoCard(stringResource(Res.string.referral_referred))
                }
            }
        }
    }
}

@Composable
private fun SignInCard(onSignIn: () -> Unit) {
    ReferralCard {
        Text(stringResource(Res.string.referral_sign_in), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onSignIn) { Text(stringResource(Res.string.referral_sign_in_action)) }
    }
}

@Composable
private fun RetryCard(onRetry: () -> Unit) {
    ReferralCard {
        Text(stringResource(Res.string.referral_load_failed), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onRetry) { Text(stringResource(Res.string.common_retry)) }
    }
}

@Composable
internal fun InfoCard(text: String) {
    ReferralCard { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.auth

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vtempe.shared.domain.account.AccountDataState
import com.vtempe.shared.domain.account.GuestDataChoice
import com.vtempe.ui.*
import com.vtempe.ui.presenter.AuthPresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** Decisions about device data after sign-in/sign-out. Rendered on top of the account screen. */
@Composable
internal fun AccountDataDialogs(state: AccountDataState, presenter: AuthPresenter) {
    when (state) {
        is AccountDataState.NeedsGuestChoice -> AccountDataDialog(
            title = Res.string.account_data_choice_title,
            message = Res.string.account_data_choice_message,
            confirm = Res.string.account_data_choice_keep_account to {
                presenter.resolveGuestChoice(GuestDataChoice.KEEP_ACCOUNT_DATA)
            },
            dismiss = Res.string.account_data_choice_keep_device to {
                presenter.resolveGuestChoice(GuestDataChoice.REPLACE_WITH_DEVICE_DATA)
            }
        )
        is AccountDataState.Failed -> AccountDataDialog(
            title = Res.string.account_data_failed_title,
            message = Res.string.account_data_failed_message,
            confirm = Res.string.account_data_retry to presenter::retryAccountData,
            dismiss = Res.string.auth_sign_out to presenter::confirmSignOut
        )
        AccountDataState.SignOutUnsynced -> AccountDataDialog(
            title = Res.string.account_data_unsynced_title,
            message = Res.string.account_data_unsynced_message,
            confirm = Res.string.account_data_unsynced_cancel to presenter::dismissSignOutWarning,
            dismiss = Res.string.account_data_unsynced_sign_out to presenter::confirmSignOut,
            onDismissRequest = presenter::dismissSignOutWarning
        )
        AccountDataState.Idle, AccountDataState.Syncing -> Unit
    }
}

@Composable
private fun AccountDataDialog(
    title: StringResource,
    message: StringResource,
    confirm: Pair<StringResource, () -> Unit>,
    dismiss: Pair<StringResource, () -> Unit>,
    // Choices about someone's data must be answered explicitly, not by tapping outside.
    onDismissRequest: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = { TextButton(onClick = confirm.second) { Text(stringResource(confirm.first)) } },
        dismissButton = { TextButton(onClick = dismiss.second) { Text(stringResource(dismiss.first)) } }
    )
}

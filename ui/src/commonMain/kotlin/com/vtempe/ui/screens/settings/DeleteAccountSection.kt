@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.shared.domain.repository.ReauthCredential
import com.vtempe.ui.*
import com.vtempe.ui.presenter.AccountDeletionUiState
import org.jetbrains.compose.resources.stringResource

/** Permanent account deletion, kept apart from the "start over" reset: confirmed first, then any
 *  re-authentication or failure the deletion runs into is shown as its own dialog. */
@Composable
internal fun DeleteAccountSection(
    deletion: AccountDeletionUiState,
    enabled: Boolean,
    onDelete: () -> Unit,
    onReauthenticate: (ReauthCredential) -> Unit,
    onDismiss: () -> Unit
) {
    var confirming by remember { mutableStateOf(false) }
    val inProgress = deletion == AccountDeletionUiState.InProgress
    Button(
        onClick = { confirming = true },
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError
        ),
        shape = MaterialTheme.shapes.large,
        enabled = enabled && !inProgress
    ) {
        if (inProgress) {
            CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onError, strokeWidth = 2.dp)
        } else {
            Text(stringResource(Res.string.settings_delete_account), fontWeight = FontWeight.Bold)
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text(stringResource(Res.string.settings_delete_account_confirm_title)) },
            text = { Text(stringResource(Res.string.settings_delete_account_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDelete()
                    }
                ) {
                    Text(
                        stringResource(Res.string.settings_delete_account_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text(stringResource(Res.string.settings_reset_cancel)) }
            }
        )
    }

    when (deletion) {
        is AccountDeletionUiState.NeedsReauthentication -> ReauthenticateDialog(deletion, onReauthenticate, onDismiss)
        AccountDeletionUiState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(Res.string.settings_delete_account_failed_title)) },
            text = { Text(stringResource(Res.string.settings_delete_account_failed_message)) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_delete_account_ok)) }
            }
        )
        AccountDeletionUiState.Idle, AccountDeletionUiState.InProgress -> Unit
    }
}

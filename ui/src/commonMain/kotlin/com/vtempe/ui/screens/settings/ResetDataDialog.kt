@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.vtempe.ui.*
import org.jetbrains.compose.resources.stringResource

/** Irreversible wipe, so it is always confirmed; signed-in users are told the account copy goes too. */
@Composable
internal fun ResetDataDialog(signedIn: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_reset_confirm_title)) },
        text = {
            Text(
                stringResource(
                    if (signedIn) Res.string.settings_reset_confirm_message_account
                    else Res.string.settings_reset_confirm_message
                )
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(Res.string.settings_reset_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_reset_cancel)) } }
    )
}

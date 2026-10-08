@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.ReauthCredential
import com.vtempe.shared.domain.repository.SignInMethod
import com.vtempe.ui.*
import com.vtempe.ui.presenter.AccountDeletionUiState
import com.vtempe.ui.presenter.CREDENTIAL_PICKER_TIMEOUT_MS
import com.vtempe.ui.screens.toStringRes
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.stringResource

/** Asks the user to sign in again with their own method: a password field for email accounts,
 *  the platform's provider (Google / Apple) otherwise. A method this platform can't offer (an
 *  Apple account on Android) gets instructions to sign out and back in instead. */
@Composable
internal fun ReauthenticateDialog(
    state: AccountDeletionUiState.NeedsReauthentication,
    onCredential: (ReauthCredential) -> Unit,
    onDismiss: () -> Unit
) {
    val provider = rememberProviderReauthentication()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var requesting by remember { mutableStateOf(false) }
    var providerError by remember { mutableStateOf<AuthErrorCode?>(null) }
    val usesPassword = state.method == SignInMethod.PASSWORD
    val usesProvider = state.method != null && state.method == provider.method
    val error = providerError ?: state.error

    fun requestProviderCredential() = scope.launch {
        requesting = true
        providerError = null
        try {
            var timedOut = true
            val credential = withTimeoutOrNull(CREDENTIAL_PICKER_TIMEOUT_MS) {
                provider.requestCredential().also { timedOut = false }
            }
            if (timedOut) providerError = AuthErrorCode.NETWORK else credential?.let(onCredential)
        } catch (failure: AuthException) {
            providerError = failure.code
        } finally {
            requesting = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_delete_account_reauth_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(
                        if (usesPassword || usesProvider) Res.string.settings_delete_account_reauth_message
                        else Res.string.settings_delete_account_reauth_unsupported
                    )
                )
                if (usesPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(Res.string.auth_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let {
                    Text(
                        stringResource(it.toStringRes()),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            when {
                usesPassword -> TextButton(
                    onClick = { onCredential(ReauthCredential.Password(password)) },
                    enabled = password.isNotEmpty()
                ) {
                    Text(stringResource(Res.string.settings_delete_account_confirm), color = MaterialTheme.colorScheme.error)
                }
                usesProvider -> TextButton(onClick = { requestProviderCredential() }, enabled = !requesting) {
                    Text(
                        stringResource(
                            if (provider.method == SignInMethod.GOOGLE) Res.string.auth_continue_with_google
                            else Res.string.auth_continue_with_apple
                        )
                    )
                }
                else -> TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_delete_account_ok)) }
            }
        },
        dismissButton = if (usesPassword || usesProvider) {
            { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.settings_reset_cancel)) } }
        } else {
            null
        }
    )
}

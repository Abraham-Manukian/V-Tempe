@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import com.vtempe.ui.presenter.AuthPresenter

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource

@Composable
fun AuthScreen(
    presenter: AuthPresenter = rememberAuthPresenter()
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    BrandScreen(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(topBarHeight + 16.dp))
            if (state.user == null) {
                SignedOutContent(state, presenter)
            } else {
                SignedInContent(state, presenter)
            }
            Spacer(Modifier.height(bottomBarHeight + 16.dp))
        }
    }
}

@Composable
private fun SignedOutContent(
    state: com.vtempe.ui.presenter.AuthUiState,
    presenter: AuthPresenter
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val emailValid = email.contains("@") && email.isNotBlank()
    val passwordValid = password.length >= 6
    val canSubmit = emailValid && passwordValid && !state.loading

    Text(
        text = stringResource(if (isSignUpMode) Res.string.auth_sign_up else Res.string.auth_sign_in),
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )

    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text(stringResource(Res.string.auth_email)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(Res.string.auth_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )

    state.errorCode?.let { code ->
        Text(
            stringResource(code.toStringRes()),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }

    Button(
        onClick = {
            if (isSignUpMode) presenter.signUp(email, password) else presenter.signIn(email, password)
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = canSubmit,
        colors = ButtonDefaults.buttonColors(
            containerColor = AiPalette.DeepAccent,
            contentColor = AiPalette.OnDeepAccent
        )
    ) {
        if (state.loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = AiPalette.OnDeepAccent)
        } else {
            Text(stringResource(if (isSignUpMode) Res.string.auth_sign_up else Res.string.auth_sign_in), fontWeight = FontWeight.Bold)
        }
    }

    TextButton(onClick = { isSignUpMode = !isSignUpMode }) {
        Text(stringResource(if (isSignUpMode) Res.string.auth_switch_to_sign_in else Res.string.auth_switch_to_sign_up))
    }
}

@Composable
private fun SignedInContent(
    state: com.vtempe.ui.presenter.AuthUiState,
    presenter: AuthPresenter
) {
    val user = state.user ?: return
    val contentColor = MaterialTheme.colorScheme.onSurface

    Text(user.email ?: user.uid, style = MaterialTheme.typography.titleMedium, color = contentColor)

    val entitlementText = when {
        state.entitlementActive == true && state.entitlementExpiresAt != null ->
            stringResource(Res.string.entitlement_active_until).kmpFormat(state.entitlementExpiresAt.toDisplayDate())
        state.entitlementActive == true -> stringResource(Res.string.entitlement_active_until_unknown)
        state.entitlementActive == false -> stringResource(Res.string.entitlement_none)
        else -> "—"
    }
    Text(entitlementText, style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.8f))

    OutlinedButton(
        onClick = { presenter.signOut() },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(Res.string.auth_sign_out))
    }
}

/** Server sends a raw ISO-8601 instant (e.g. "2026-08-15T10:23:45Z") — showing just the date
 *  part is enough for a subscription-expiry label; a full date/time formatter isn't worth
 *  pulling in for this one line. */
private fun String.toDisplayDate(): String = substringBefore('T')

private fun AuthErrorCode.toStringRes() = when (this) {
    AuthErrorCode.INVALID_CREDENTIALS -> Res.string.auth_error_invalid_credentials
    AuthErrorCode.WEAK_PASSWORD -> Res.string.auth_error_weak_password
    AuthErrorCode.EMAIL_IN_USE -> Res.string.auth_error_email_in_use
    AuthErrorCode.NETWORK -> Res.string.auth_error_network
    AuthErrorCode.UNAVAILABLE -> Res.string.auth_error_unavailable
    AuthErrorCode.UNKNOWN -> Res.string.auth_error_generic
}

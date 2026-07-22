@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens

import com.vtempe.ui.*
import com.vtempe.ui.presenter.AuthPresenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vtempe.core.designsystem.components.BrandScreen
import com.vtempe.core.designsystem.icons.AiIcons
import com.vtempe.core.designsystem.theme.AiPalette
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.stringResource

/** Platform-specific social sign-in entry point — Android shows "Continue with Google" via
 *  Credential Manager, iOS shows "Continue with Apple" via AuthenticationServices. Each actual
 *  obtains its own platform credential and hands the resulting token to [presenter]. */
@Composable
expect fun SocialSignInButtons(presenter: AuthPresenter)

@Composable
fun AuthScreen(
    presenter: AuthPresenter = rememberAuthPresenter(),
    /** Fires whenever [presenter] reports a signed-in user — on first entering this screen
     *  already-signed-in, and after a fresh sign-in. Used by the pre-onboarding
     *  [com.vtempe.ui.navigation.Destination.Welcome] screen to advance to onboarding either
     *  way; left null for the plain account-settings screen. */
    onAuthenticated: (() -> Unit)? = null,
    /** Shows a "continue without an account" link when non-null. */
    onSkip: (() -> Unit)? = null
) {
    val state by presenter.state.collectAsState()
    val topBarHeight = LocalTopBarHeight.current
    val bottomBarHeight = LocalBottomBarHeight.current

    LaunchedEffect(state.user != null) {
        if (state.user != null) onAuthenticated?.invoke()
    }

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
                SignedOutContent(state, presenter, onSkip)
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
    presenter: AuthPresenter,
    onSkip: (() -> Unit)? = null
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val emailValid = email.contains("@") && email.isNotBlank()
    val passwordValid = password.length >= 6
    val canSubmit = emailValid && passwordValid && !state.loading

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                AiIcons.Strength,
                contentDescription = null,
                tint = AiPalette.OnGradient,
                modifier = Modifier.size(32.dp)
            )
        }
        Text(
            text = stringResource(if (isSignUpMode) Res.string.auth_sign_up else Res.string.auth_sign_in),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = AiPalette.OnGradient,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(Res.string.auth_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = AiPalette.OnGradient.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }

    // The email/password form and its buttons need normal (light-surface) Material contrast —
    // OutlinedTextField/OutlinedButton default colors are tuned for a light background, and
    // become nearly invisible directly on the brand gradient (green-on-green). A plain white
    // card with a soft shadow gives them one to sit on instead.
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SocialSignInButtons(presenter)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Text(
                    stringResource(Res.string.auth_or_divider),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(Res.string.auth_email)) },
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AiPalette.DeepAccent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(Res.string.auth_password)) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AiPalette.DeepAccent),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
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
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(16.dp),
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

            TextButton(
                onClick = { isSignUpMode = !isSignUpMode },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (isSignUpMode) Res.string.auth_switch_to_sign_in else Res.string.auth_switch_to_sign_up))
            }

            if (onSkip != null) {
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.auth_skip))
                }
            }
        }
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

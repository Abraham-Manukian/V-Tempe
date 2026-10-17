@file:OptIn(org.jetbrains.compose.resources.ExperimentalResourceApi::class)

package com.vtempe.ui.screens.referral

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vtempe.shared.domain.referral.AttachCodeResult
import com.vtempe.shared.domain.referral.ReferralOverview
import com.vtempe.ui.*
import com.vtempe.ui.util.formatMoney
import com.vtempe.ui.util.kmpFormat
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ReferralCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.97f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
internal fun InviteCard(
    overview: ReferralOverview,
    requestingCode: Boolean,
    subscriptionRequired: Boolean,
    codeRequestFailed: Boolean,
    onRequestCode: () -> Unit,
) {
    val share = rememberShareText()
    val clipboard = LocalClipboardManager.current
    ReferralCard {
        Text(stringResource(Res.string.referral_invite_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        // Rules come from the server; an older server (no fields) or rewards switched off (0%)
        // must not promise "0%".
        if (overview.sharePercent > 0) {
            Text(
                stringResource(Res.string.referral_rules).kmpFormat(overview.sharePercent, overview.holdDays),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        val code = overview.code
        when {
            code != null && overview.canInvite -> {
                Text(code, style = MaterialTheme.typography.headlineMedium, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                val shareText = stringResource(Res.string.referral_share_text).kmpFormat(code)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Text(stringResource(Res.string.referral_copy))
                    }
                    Button(onClick = { share(shareText) }) { Text(stringResource(Res.string.referral_share)) }
                }
            }
            overview.canInvite -> Button(onClick = onRequestCode, enabled = !requestingCode) {
                Text(stringResource(Res.string.referral_get_code))
            }
            else -> Text(stringResource(Res.string.referral_subscribers_only), style = MaterialTheme.typography.bodyMedium)
        }
        if (subscriptionRequired) Text(stringResource(Res.string.referral_subscribers_only), color = MaterialTheme.colorScheme.error)
        if (codeRequestFailed) Text(stringResource(Res.string.referral_try_again), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
internal fun BalanceCard(overview: ReferralOverview) {
    ReferralCard {
        Text(stringResource(Res.string.referral_balance_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            stringResource(Res.string.referral_stats).kmpFormat(overview.invitedCount, overview.rewardedCount),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (overview.balances.isEmpty()) {
            Text(stringResource(Res.string.referral_balance_empty), style = MaterialTheme.typography.bodyMedium)
        }
        overview.balances.forEach { money ->
            Text(
                stringResource(Res.string.referral_balance_line).kmpFormat(
                    formatMoney(money.availableMinor, money.currency),
                    formatMoney(money.pendingMinor, money.currency),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        if (!overview.spendingAvailable) {
            Text(
                stringResource(Res.string.referral_spending_soon),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
internal fun EnterCodeCard(
    input: String,
    attaching: Boolean,
    result: AttachCodeResult?,
    onInput: (String) -> Unit,
    onApply: () -> Unit,
) {
    ReferralCard {
        Text(stringResource(Res.string.referral_enter_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            singleLine = true,
            label = { Text(stringResource(Res.string.referral_enter_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onApply, enabled = input.isNotBlank() && !attaching) {
            Text(stringResource(Res.string.referral_enter_apply))
        }
        result?.let { outcome ->
            val ok = outcome == AttachCodeResult.ATTACHED
            Text(
                stringResource(attachMessage(outcome)),
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun attachMessage(result: AttachCodeResult): StringResource = when (result) {
    AttachCodeResult.ATTACHED -> Res.string.referral_attached
    AttachCodeResult.INVALID_CODE -> Res.string.referral_error_invalid
    AttachCodeResult.UNKNOWN_CODE -> Res.string.referral_error_unknown
    AttachCodeResult.SELF_REFERRAL -> Res.string.referral_error_self
    AttachCodeResult.ALREADY_REFERRED -> Res.string.referral_error_already_referred
    AttachCodeResult.ALREADY_PAID -> Res.string.referral_error_already_paid
    AttachCodeResult.CODE_INACTIVE -> Res.string.referral_error_inactive
    AttachCodeResult.ACCOUNT_DELETED -> Res.string.referral_error_account_deleted
    AttachCodeResult.SIGN_IN_REQUIRED -> Res.string.referral_sign_in
    AttachCodeResult.FAILED -> Res.string.referral_try_again
}

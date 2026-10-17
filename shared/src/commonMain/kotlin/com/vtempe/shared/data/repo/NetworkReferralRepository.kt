package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.data.network.dto.AttachReferralRequestDto
import com.vtempe.shared.data.network.dto.ReferralCodeDto
import com.vtempe.shared.data.network.dto.ReferralErrorDto
import com.vtempe.shared.data.network.dto.ReferralSummaryDto
import com.vtempe.shared.domain.referral.AttachCodeResult
import com.vtempe.shared.domain.referral.ReferralCodeResult
import com.vtempe.shared.domain.referral.ReferralMoney
import com.vtempe.shared.domain.referral.ReferralOverview
import com.vtempe.shared.domain.referral.ReferralRepository
import com.vtempe.shared.domain.util.DataResult
import com.vtempe.shared.domain.util.map
import kotlinx.serialization.json.Json

/** /me/referral over [ApiClient]; the Firebase token is added by the client's BearerAuth plugin. */
class NetworkReferralRepository(private val api: ApiClient) : ReferralRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun overview(): DataResult<ReferralOverview> =
        api.getResult<ReferralSummaryDto>("/me/referral").map { dto ->
            ReferralOverview(
                code = dto.code,
                canInvite = dto.canInvite,
                referred = dto.referred,
                invitedCount = dto.invitedCount,
                rewardedCount = dto.rewardedCount,
                balances = dto.balances.map { ReferralMoney(it.currency, it.availableMinor, it.pendingMinor) },
                spendingAvailable = dto.redeemOffers.isNotEmpty(),
                sharePercent = dto.sharePercent,
                holdDays = dto.holdDays,
            )
        }

    override suspend fun requestCode(): ReferralCodeResult =
        when (val result = api.postResult<Map<String, String>, ReferralCodeDto>("/me/referral/code", emptyMap())) {
            is DataResult.Success -> ReferralCodeResult.Issued(result.data.code)
            is DataResult.Failure ->
                if (result.code == 403 && errorOf(result) == "subscription_required") ReferralCodeResult.SubscriptionRequired
                else ReferralCodeResult.Failed(result)
        }

    override suspend fun attach(code: String): AttachCodeResult =
        when (val result = api.postNoContent("/me/referral/attach", AttachReferralRequestDto(code))) {
            is DataResult.Success -> AttachCodeResult.ATTACHED
            is DataResult.Failure -> when {
                result.code == 401 -> AttachCodeResult.SIGN_IN_REQUIRED
                result.reason != DataResult.Reason.Http -> AttachCodeResult.FAILED
                else -> when (errorOf(result)) {
                    "invalid_code", "invalid_request" -> AttachCodeResult.INVALID_CODE
                    "unknown_code" -> AttachCodeResult.UNKNOWN_CODE
                    "self_referral" -> AttachCodeResult.SELF_REFERRAL
                    "already_referred" -> AttachCodeResult.ALREADY_REFERRED
                    "already_paid" -> AttachCodeResult.ALREADY_PAID
                    "code_inactive" -> AttachCodeResult.CODE_INACTIVE
                    "account_deleted" -> AttachCodeResult.ACCOUNT_DELETED
                    else -> AttachCodeResult.FAILED
                }
            }
        }

    private fun errorOf(failure: DataResult.Failure): String? =
        failure.rawPayload?.let { raw -> runCatching { json.decodeFromString<ReferralErrorDto>(raw).error }.getOrNull() }
}

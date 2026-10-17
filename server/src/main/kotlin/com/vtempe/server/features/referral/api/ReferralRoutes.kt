package com.vtempe.server.features.referral.api

import com.vtempe.server.features.auth.UserIdKey
import com.vtempe.server.features.referral.data.service.ReferralService
import com.vtempe.server.features.referral.domain.model.AttachResult
import com.vtempe.server.features.referral.domain.model.IssueCodeResult
import com.vtempe.server.features.referral.domain.model.RedeemResult
import com.vtempe.server.shared.dto.referral.AttachReferralRequest
import com.vtempe.server.shared.dto.referral.RedeemOfferDto
import com.vtempe.server.shared.dto.referral.RedeemReferralRequest
import com.vtempe.server.shared.dto.referral.RedeemReferralResponse
import com.vtempe.server.shared.dto.referral.ReferralBalanceDto
import com.vtempe.server.shared.dto.referral.ReferralCodeResponse
import com.vtempe.server.shared.dto.referral.ReferralErrorResponse
import com.vtempe.server.shared.dto.referral.ReferralSummaryResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.request.ContentTransformationException
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.koin.ktor.ext.inject

/**
 * Mounted on /me (registerUserRoutes), so UserAuthPlugin has already verified the caller.
 * No extra rate limit on /attach: codes are meant to be shared, guessing one gains nothing
 * (a user can be attached once, before paying), and every call needs a valid Firebase token.
 */
fun Route.registerReferralRoutes() {
    val referrals: ReferralService by inject()

    route("/referral") {
        get {
            val userId = call.userIdOrNull() ?: return@get call.respond(HttpStatusCode.Unauthorized)
            val summary = referrals.summary(userId)
            call.respond(
                ReferralSummaryResponse(
                    code = summary.code,
                    canInvite = summary.canInvite,
                    referred = summary.referred,
                    invitedCount = summary.invitedCount,
                    rewardedCount = summary.rewardedCount,
                    balances = summary.balances.map { ReferralBalanceDto(it.currency, it.availableMinor, it.pendingMinor) },
                    redeemOffers = summary.offers.map { RedeemOfferDto(it.currency, it.priceMinor, it.periodDays) },
                    sharePercent = summary.sharePercent,
                    holdDays = summary.holdDays,
                )
            )
        }

        post("/code") {
            val userId = call.userIdOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            when (val result = referrals.issueCode(userId)) {
                is IssueCodeResult.Issued -> call.respond(ReferralCodeResponse(result.code))
                IssueCodeResult.SubscriptionRequired ->
                    call.respond(HttpStatusCode.Forbidden, ReferralErrorResponse("subscription_required"))
            }
        }

        attachRoute()

        post("/redeem") {
            val userId = call.userIdOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)
            val request = call.receiveOrNull<RedeemReferralRequest>() ?: return@post call.invalidRequest()
            when (val result = referrals.redeem(userId, request.currency, request.requestId)) {
                is RedeemResult.Redeemed -> call.respond(RedeemReferralResponse(result.expiresAt?.toString()))
                is RedeemResult.Insufficient -> call.respond(
                    HttpStatusCode.Conflict,
                    ReferralErrorResponse("insufficient_balance", result.availableMinor, result.priceMinor),
                )
                RedeemResult.NoOffer -> call.respond(HttpStatusCode.UnprocessableEntity, ReferralErrorResponse("no_offer_for_currency"))
                RedeemResult.InvalidRequest -> call.respond(HttpStatusCode.BadRequest, ReferralErrorResponse("invalid_request_id"))
                RedeemResult.RequestReused -> call.respond(HttpStatusCode.Conflict, ReferralErrorResponse("request_id_reused"))
                RedeemResult.AccountClosed -> call.respond(HttpStatusCode.Gone, ReferralErrorResponse("account_deleted"))
            }
        }
    }
}

private fun Route.attachRoute() {
    // Resolved on the first request, like every route here: registering routes needs no bean.
    val referrals: ReferralService by inject()

    post("/attach") {
        val userId = call.userIdOrNull() ?: return@post call.respond(HttpStatusCode.Unauthorized)
        val request = call.receiveOrNull<AttachReferralRequest>() ?: return@post call.invalidRequest()
        val (status, error) = when (referrals.attach(userId, request.code)) {
            AttachResult.ATTACHED -> return@post call.respond(HttpStatusCode.NoContent)
            AttachResult.INVALID_CODE -> HttpStatusCode.BadRequest to "invalid_code"
            AttachResult.SELF_REFERRAL -> HttpStatusCode.BadRequest to "self_referral"
            AttachResult.UNKNOWN_CODE -> HttpStatusCode.NotFound to "unknown_code"
            AttachResult.ALREADY_ATTACHED -> HttpStatusCode.Conflict to "already_referred"
            AttachResult.ALREADY_PAID -> HttpStatusCode.Conflict to "already_paid"
            AttachResult.CODE_INACTIVE -> HttpStatusCode.Conflict to "code_inactive"
            AttachResult.ACCOUNT_CLOSED -> HttpStatusCode.Gone to "account_deleted"
        }
        call.respond(status, ReferralErrorResponse(error))
    }
}

/** The body as [T], or null when it is not valid JSON of that shape (missing field, wrong type,
 *  wrong or missing Content-Type): such a request gets 400 `invalid_request`, never a bare status. */
private suspend inline fun <reified T : Any> ApplicationCall.receiveOrNull(): T? = try {
    receive<T>()
} catch (e: ContentTransformationException) {
    null
} catch (e: BadRequestException) {
    null
}

private suspend fun ApplicationCall.invalidRequest() =
    respond(HttpStatusCode.BadRequest, ReferralErrorResponse("invalid_request"))

// Set by UserAuthPlugin on the /me subtree, which already answered 401 for a missing or invalid
// token; absent only if mounted outside it.
private fun ApplicationCall.userIdOrNull(): String? = attributes.getOrNull(UserIdKey)

package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.domain.referral.AttachCodeResult
import com.vtempe.shared.domain.referral.ReferralCodeResult
import com.vtempe.shared.domain.referral.ReferralMoney
import com.vtempe.shared.domain.util.DataResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The /me/referral wire contract as the client reads it (server: ReferralRoutes / ReferralDtos). */
class NetworkReferralRepositoryTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repository(handler: MockEngine): NetworkReferralRepository =
        NetworkReferralRepository(ApiClient(HttpClient(handler) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }, "http://api"))

    @Test
    fun overviewMapsTheServerSummary() = runBlocking {
        val repo = repository(MockEngine { request ->
            assertEquals("/me/referral", request.url.encodedPath)
            respond(
                """{"code":"BRSKCDM2","canInvite":true,"referred":false,"invitedCount":3,"rewardedCount":1,
                   "balances":[{"currency":"RUB","availableMinor":5980,"pendingMinor":2000}],
                   "redeemOffers":[],"sharePercent":20,"holdDays":30,"futureField":true}""",
                HttpStatusCode.OK, json,
            )
        })
        val overview = (repo.overview() as DataResult.Success).data
        assertEquals("BRSKCDM2", overview.code)
        assertEquals(listOf(ReferralMoney("RUB", 5980, 2000)), overview.balances)
        assertEquals(false, overview.spendingAvailable)
        assertEquals(20 to 30, overview.sharePercent to overview.holdDays)
        assertEquals(3 to 1, overview.invitedCount to overview.rewardedCount)
    }

    @Test
    fun aSignedOutOverviewIsA401Failure() = runBlocking {
        val repo = repository(MockEngine { respond("""{"error":"Unauthorized"}""", HttpStatusCode.Unauthorized, json) })
        assertEquals(401, (repo.overview() as DataResult.Failure).code)
    }

    @Test
    fun codeRequestDistinguishesNotSubscribedFromFailure() = runBlocking {
        assertEquals(ReferralCodeResult.Issued("BRSKCDM2"),
            repository(MockEngine { respond("""{"code":"BRSKCDM2"}""", HttpStatusCode.OK, json) }).requestCode())
        assertEquals(ReferralCodeResult.SubscriptionRequired,
            repository(MockEngine { respond("""{"error":"subscription_required"}""", HttpStatusCode.Forbidden, json) }).requestCode())
        assertTrue(repository(MockEngine { respond("", HttpStatusCode.InternalServerError, json) }).requestCode() is ReferralCodeResult.Failed)
    }

    @Test
    fun attachSendsTheCodeAndMapsEveryServerError() = runBlocking {
        var sent = ""
        val ok = repository(MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            sent = (request.body as TextContent).text
            respond("", HttpStatusCode.NoContent)
        })
        assertEquals(AttachCodeResult.ATTACHED, ok.attach("BRSKCDM2"))
        assertEquals("""{"code":"BRSKCDM2"}""", sent)

        val cases = mapOf(
            (400 to "invalid_code") to AttachCodeResult.INVALID_CODE,
            (400 to "invalid_request") to AttachCodeResult.INVALID_CODE,
            (400 to "self_referral") to AttachCodeResult.SELF_REFERRAL,
            (404 to "unknown_code") to AttachCodeResult.UNKNOWN_CODE,
            (409 to "already_referred") to AttachCodeResult.ALREADY_REFERRED,
            (409 to "already_paid") to AttachCodeResult.ALREADY_PAID,
            (409 to "code_inactive") to AttachCodeResult.CODE_INACTIVE,
            (410 to "account_deleted") to AttachCodeResult.ACCOUNT_DELETED,
            (401 to "Unauthorized") to AttachCodeResult.SIGN_IN_REQUIRED,
            (503 to "storage_unavailable") to AttachCodeResult.FAILED,
            (404 to "") to AttachCodeResult.FAILED,
        )
        for ((answer, expected) in cases) {
            val (status, error) = answer
            val repo = repository(MockEngine {
                respond(if (error.isEmpty()) "" else """{"error":"$error"}""", HttpStatusCode.fromValue(status), json)
            })
            assertEquals(expected, repo.attach("BRSKCDM2"), "$status $error")
        }
    }

    @Test
    fun anOlderServerWithoutRulesDecodesAndAMissingAmountIsAnError() = runBlocking {
        val older = repository(MockEngine {
            respond("""{"code":null,"canInvite":false,"referred":false,"invitedCount":0,"rewardedCount":0,
                "balances":[{"currency":"RUB","availableMinor":-2000,"pendingMinor":0}],"redeemOffers":[{"currency":"RUB","priceMinor":29900,"periodDays":30}]}""",
                HttpStatusCode.OK, json)
        })
        val overview = (older.overview() as DataResult.Success).data
        assertEquals(0 to 0, overview.sharePercent to overview.holdDays)
        assertEquals(listOf(ReferralMoney("RUB", -2000, 0)), overview.balances, "negative balances survive")
        assertEquals(true, overview.spendingAvailable)

        val broken = repository(MockEngine {
            respond("""{"balances":[{"currency":"RUB","pending":5}]}""", HttpStatusCode.OK, json)
        })
        assertEquals(DataResult.Reason.InvalidFormat, (broken.overview() as DataResult.Failure).reason,
            "a renamed amount must not read as 0")
    }
}

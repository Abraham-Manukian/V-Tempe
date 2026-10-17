package com.vtempe.server

import com.vtempe.server.app.registerUserRoutes
import com.vtempe.server.features.auth.IdTokenVerifier
import com.vtempe.server.features.entitlement.data.repo.InMemoryEntitlementRepository
import com.vtempe.server.features.entitlement.data.service.EntitlementService
import com.vtempe.server.features.entitlement.domain.model.PaymentSource
import com.vtempe.server.features.referral.data.repo.InMemoryReferralRepository
import com.vtempe.server.features.referral.data.service.ReferralService
import com.vtempe.server.features.referral.domain.model.RedeemOffer
import com.vtempe.server.features.referral.domain.model.ReferralPolicy
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Duration

/** The HTTP contract of /me/referral: identity from the token only, stable status codes and error bodies. */
class ReferralRoutesTest {
    @AfterEach
    fun tearDown() {
        runCatching { stopKoin() }
    }

    private val verifier = IdTokenVerifier { token -> token.removePrefix("token-").takeIf { token.startsWith("token-") } }
    private val entitlements = EntitlementService(InMemoryEntitlementRepository())
    private val service = ReferralService(
        InMemoryReferralRepository(), entitlements,
        ReferralPolicy(20, Duration.ofDays(30), listOf(RedeemOffer("RUB", 2000, 7))),
        newCode = { "BRSKCDM2" },
    )

    private fun routesTest(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            install(Koin) { modules(module { single { service } }) }
            install(ContentNegotiation) { json() }
            routing { registerUserRoutes(verifier) }
        }
        block(client)
    }

    private suspend fun HttpClient.postJson(path: String, user: String, body: String): HttpResponse = post(path) {
        header(HttpHeaders.Authorization, "Bearer token-$user")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    private suspend fun HttpResponse.error(): String =
        Json.parseToJsonElement(bodyAsText()).jsonObject["error"]!!.jsonPrimitive.content

    @Test
    fun `codes, attachment and redemption answer with stable statuses`() = routesTest { client ->
        val noSub = client.postJson("/me/referral/code", "boris", "")
        assertEquals(HttpStatusCode.Forbidden, noSub.status)
        assertEquals("subscription_required", noSub.error())

        runBlocking { entitlements.recordPaymentAndExtend("p1", "boris", PaymentSource.YOOKASSA, 29900, "RUB", "{}", Duration.ofDays(30)) }
        val issued = client.postJson("/me/referral/code", "boris", "")
        assertEquals(HttpStatusCode.OK, issued.status)
        assertTrue(issued.bodyAsText().contains("BRSKCDM2"))

        assertEquals(HttpStatusCode.BadRequest, client.postJson("/me/referral/attach", "anna", """{"code":"??"}""").status)
        val self = client.postJson("/me/referral/attach", "boris", """{"code":"BRSKCDM2"}""")
        assertEquals(HttpStatusCode.BadRequest to "self_referral", self.status to self.error())
        assertEquals(HttpStatusCode.NotFound, client.postJson("/me/referral/attach", "anna", """{"code":"ZZZZZZZ2"}""").status)
        assertEquals(HttpStatusCode.NoContent, client.postJson("/me/referral/attach", "anna", """{"code":"brskcdm2"}""").status)

        val broke = client.postJson("/me/referral/redeem", "boris", """{"currency":"RUB","requestId":"req-00001"}""")
        assertEquals(HttpStatusCode.Conflict to "insufficient_balance", broke.status to broke.error())
        assertEquals(HttpStatusCode.UnprocessableEntity,
            client.postJson("/me/referral/redeem", "boris", """{"currency":"USD","requestId":"req-00001"}""").status)
        assertEquals(HttpStatusCode.BadRequest,
            client.postJson("/me/referral/redeem", "boris", """{"currency":"RUB","requestId":"x"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.postJson("/me/referral/redeem", "boris", """{"currency":1}""").status)
    }

    @Test
    fun `the summary belongs to the token's user`() = routesTest { client ->
        runBlocking { entitlements.recordPaymentAndExtend("p1", "boris", PaymentSource.YOOKASSA, 29900, "RUB", "{}", Duration.ofDays(30)) }
        client.postJson("/me/referral/code", "boris", "")

        val boris = client.get("/me/referral") { header(HttpHeaders.Authorization, "Bearer token-boris") }
        assertEquals(HttpStatusCode.OK, boris.status)
        val body = Json.parseToJsonElement(boris.bodyAsText()).jsonObject
        assertEquals(setOf("code", "canInvite", "referred", "invitedCount", "rewardedCount", "balances", "redeemOffers",
            "sharePercent", "holdDays"), body.keys, "the deployed-client contract")
        assertEquals("BRSKCDM2", body["code"]!!.jsonPrimitive.content)
        assertEquals("true", body["canInvite"]!!.jsonPrimitive.content)
        assertEquals("20", body["sharePercent"]!!.jsonPrimitive.content)
        assertEquals("30", body["holdDays"]!!.jsonPrimitive.content)
        assertTrue(boris.bodyAsText().contains(""""redeemOffers":[{"currency":"RUB","priceMinor":2000,"periodDays":7}]"""))

        val anna = client.get("/me/referral") { header(HttpHeaders.Authorization, "Bearer token-anna") }
        val annaBody = Json.parseToJsonElement(anna.bodyAsText()).jsonObject
        assertEquals("null", annaBody["code"].toString())
        assertEquals("false", annaBody["canInvite"]!!.jsonPrimitive.content)
    }

    @Test
    fun `malformed bodies get 400 invalid_request with a body, never a bare status`() = routesTest { client ->
        for ((path, body) in listOf(
            "/me/referral/attach" to "{not json",
            "/me/referral/attach" to "{}",
            "/me/referral/attach" to "",
            "/me/referral/redeem" to """{"currency":1}""",
            "/me/referral/redeem" to """{"currency":"RUB"}""",
        )) {
            val response = client.postJson(path, "anna", body)
            assertEquals(HttpStatusCode.BadRequest to "invalid_request", response.status to response.error(), "$path $body")
        }
        val noContentType = client.post("/me/referral/attach") {
            header(HttpHeaders.Authorization, "Bearer token-anna")
            setBody("""{"code":"BRSKCDM2"}""")
        }
        assertEquals(HttpStatusCode.BadRequest to "invalid_request", noContentType.status to noContentType.error())
    }

    @Test
    fun `every attach outcome has its own status and error code`() = routesTest { client ->
        runBlocking {
            entitlements.recordPaymentAndExtend("p1", "boris", PaymentSource.YOOKASSA, 29900, "RUB", "{}", Duration.ofDays(30))
            entitlements.recordPaymentAndExtend("p2", "dima", PaymentSource.YOOKASSA, 29900, "RUB", "{}", Duration.ofDays(30))
        }
        client.postJson("/me/referral/code", "boris", "")
        val cases = listOf(
            Triple("gleb", "??", HttpStatusCode.BadRequest to "invalid_code"),
            Triple("gleb", "ZZZZZZZ2", HttpStatusCode.NotFound to "unknown_code"),
            Triple("boris", "BRSKCDM2", HttpStatusCode.BadRequest to "self_referral"),
            Triple("dima", "BRSKCDM2", HttpStatusCode.Conflict to "already_paid"),
        )
        for ((user, code, expected) in cases) {
            val response = client.postJson("/me/referral/attach", user, """{"code":"$code"}""")
            assertEquals(expected, response.status to response.error(), "$user $code")
        }
    }
}

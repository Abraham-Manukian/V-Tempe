package com.vtempe.shared.data.network

import com.vtempe.shared.data.network.dto.EntitlementDto
import com.vtempe.shared.data.repo.NetworkEntitlementRepository
import com.vtempe.shared.domain.util.DataResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** VT-AUD-076: a non-2xx answer is never a success, and its status reaches the caller. */
class HttpStatusClassificationTest {

    @Serializable private data class Body(val value: String = "")

    private fun api(status: HttpStatusCode, body: String): ApiClient {
        val engine = MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }
        return ApiClient(HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }, "http://test")
    }

    @Test
    fun nonSuccessStatusesAreHttpFailuresWithTheirCodeWhateverTheBody() = runBlocking<Unit> {
        val statuses = listOf(HttpStatusCode.Unauthorized, HttpStatusCode.Forbidden, HttpStatusCode.InternalServerError, HttpStatusCode.ServiceUnavailable)
        // Including a body that fits the DTO: before the fix, {} or {"error":...} decoded as "free plan".
        val bodies = listOf("", "{}", """{"error":"denied"}""", """{"active":true}""")
        for (status in statuses) for (body in bodies) {
            val result = NetworkEntitlementRepository(api(status, body)).fetchEntitlement()
            val failure = assertIs<DataResult.Failure>(result, "$status / '$body'")
            assertEquals(DataResult.Reason.Http, failure.reason)
            assertEquals(status.value, failure.code)
            assertEquals("HTTP ${status.value}", failure.message, "no response body in the message")
        }
    }

    @Test
    fun aSuccessfulAnswerIsDecodedAndAMalformedOneStaysInvalidFormat() = runBlocking<Unit> {
        val ok = assertIs<DataResult.Success<EntitlementDto>>(
            NetworkEntitlementRepository(api(HttpStatusCode.OK, """{"active":true}""")).fetchEntitlement()
        )
        assertEquals(true, ok.data.active)

        val malformed = assertIs<DataResult.Failure>(api(HttpStatusCode.OK, "not json").getResult<Body>("/x"))
        assertEquals(DataResult.Reason.InvalidFormat, malformed.reason)
    }

    @Test
    fun putAndDeleteKeepWhyTheyFailed() = runBlocking<Unit> {
        assertIs<DataResult.Success<Unit>>(api(HttpStatusCode.NoContent, "").putNoContent("/me/sync/weight", Body()))
        val forbidden = assertIs<DataResult.Failure>(api(HttpStatusCode.Forbidden, "").deleteNoContent("/me"))
        assertEquals(403, forbidden.code)

        val offline = ApiClient(
            HttpClient(MockEngine { throw IOException("offline") }) { install(ContentNegotiation) { json() } },
            "http://test",
        )
        assertEquals(DataResult.Reason.Network, assertIs<DataResult.Failure>(offline.putNoContent("/me/sync/weight", Body())).reason)
    }
}

package com.vtempe.shared.data.sync

import com.vtempe.shared.domain.account.AccountDataFailure
import com.vtempe.shared.domain.account.AccountDataOutcome
import com.vtempe.shared.domain.account.AccountDataState
import com.vtempe.shared.domain.account.DefaultAccountSession
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** VT-AUD-076: a failed account-data load says why, so the user is told what to do about it. */
class AccountDataFailureReasonTest {
    private val device = TestDevice()

    @AfterTest
    fun tearDown() = device.close()

    private fun signInFailing(status: HttpStatusCode) {
        device.auth.signInAs("anna")
        device.server.failRequests = true
        device.server.failStatus = status
    }

    @Test
    fun rejectedSignInAsksToSignInAgain() = runBlocking {
        signInFailing(HttpStatusCode.Unauthorized)
        assertEquals(AccountDataOutcome.Failed(AccountDataFailure.SESSION_EXPIRED), device.coordinator.onSignedIn("anna"))
    }

    @Test
    fun refusedAccountIsAccessDenied() = runBlocking {
        signInFailing(HttpStatusCode.Forbidden)
        assertEquals(AccountDataOutcome.Failed(AccountDataFailure.ACCESS_DENIED), device.coordinator.onSignedIn("anna"))
    }

    @Test
    fun serverErrorIsNotBlamedOnTheConnection() = runBlocking {
        signInFailing(HttpStatusCode.InternalServerError)
        assertEquals(AccountDataOutcome.Failed(AccountDataFailure.SERVER), device.coordinator.onSignedIn("anna"))
    }

    @Test
    fun noConnectionIsOffline() = runBlocking {
        device.auth.signInAs("anna")
        device.server.offline = true
        assertEquals(AccountDataOutcome.Failed(AccountDataFailure.OFFLINE), device.coordinator.onSignedIn("anna"))
        assertNull(device.owner.ownerUid())
    }

    @Test
    fun accountNoLongerSignedInIsSessionExpired() = runBlocking {
        device.auth.signInAs("boris")
        assertEquals(AccountDataOutcome.Failed(AccountDataFailure.SESSION_EXPIRED), device.coordinator.onSignedIn("anna"))
    }

    @Test
    fun theReasonReachesTheSessionStateTheDialogReads() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val session = DefaultAccountSession(device.coordinator, device.auth, device.owner, scope)
            signInFailing(HttpStatusCode.Forbidden)

            session.onSignedIn("anna")

            val settled = withTimeout(5_000) { session.state.first { it != AccountDataState.Syncing } }
            assertEquals(AccountDataState.Failed("anna", AccountDataFailure.ACCESS_DENIED), settled)
        } finally {
            scope.cancel()
        }
    }
}

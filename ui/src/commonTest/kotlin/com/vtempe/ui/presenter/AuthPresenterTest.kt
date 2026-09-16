package com.vtempe.ui.presenter

import com.vtempe.shared.data.network.dto.EntitlementDto
import com.vtempe.shared.domain.account.AccountDataState
import com.vtempe.shared.domain.account.AccountSession
import com.vtempe.shared.domain.account.GuestDataChoice
import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.AuthUser
import com.vtempe.shared.domain.repository.EntitlementRepository
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AuthPresenterTest {
    @Test
    fun stalledSignInTimesOutAndAllowsRetry() = runTest {
        val auth = FakeAuth()
        val session = FakeSession()
        val presenter = AuthPresenterDelegate(auth, entitlement, session, backgroundScope)
        auth.action = { awaitCancellation() }
        presenter.signIn("user@example.test", "password")
        runCurrent()
        assertTrue(presenter.state.value.loading)

        advanceTimeBy(AUTH_REQUEST_TIMEOUT_MS)
        runCurrent()
        assertFalse(presenter.state.value.loading)
        assertEquals(AuthErrorCode.NETWORK, presenter.state.value.errorCode)
        assertTrue(session.signedIn.isEmpty())

        auth.action = { user }
        presenter.signIn("user@example.test", "password")
        runCurrent()
        assertFalse(presenter.state.value.loading)
        assertNull(presenter.state.value.errorCode)
        assertEquals(user, presenter.state.value.user)
        assertEquals(listOf(user.uid), session.signedIn)
    }

    @Test
    fun duplicateRequestsDoNotStartAnotherAuthentication() = runTest {
        val auth = FakeAuth().apply { action = { awaitCancellation() } }
        val presenter = AuthPresenterDelegate(auth, entitlement, FakeSession(), backgroundScope)
        presenter.signIn("user@example.test", "password")
        presenter.signUp("user@example.test", "password")
        presenter.signInWithGoogle("test-token")
        runCurrent()
        assertEquals(1, auth.calls)
        assertTrue(presenter.state.value.loading)
    }

    @Test
    fun networkFailureClearsLoadingAndKeepsErrorClassification() = runTest {
        val auth = FakeAuth().apply {
            action = { throw AuthException(AuthErrorCode.NETWORK, "offline") }
        }
        val presenter = AuthPresenterDelegate(auth, entitlement, FakeSession(), backgroundScope)
        presenter.signInWithApple("test-token", "test-nonce")
        runCurrent()
        assertFalse(presenter.state.value.loading)
        assertEquals(AuthErrorCode.NETWORK, presenter.state.value.errorCode)
    }

    @Test
    fun cancellationDoesNotBecomeAUserVisibleError() = runTest {
        val auth = FakeAuth().apply { action = { throw CancellationException("screen closed") } }
        val session = FakeSession()
        val presenter = AuthPresenterDelegate(auth, entitlement, session, backgroundScope)
        presenter.signIn("user@example.test", "password")
        runCurrent()
        assertFalse(presenter.state.value.loading)
        assertNull(presenter.state.value.errorCode)
        assertTrue(session.signedIn.isEmpty())
    }

    @Test
    fun closingOwnerCancelsPendingSignInAndClearsLoading() = runTest {
        val ownerJob = SupervisorJob(backgroundScope.coroutineContext[Job])
        val ownerScope = CoroutineScope(backgroundScope.coroutineContext + ownerJob)
        var actionCancelled = false
        val auth = FakeAuth().apply {
            action = {
                try {
                    awaitCancellation()
                } finally {
                    actionCancelled = true
                }
            }
        }
        val presenter = AuthPresenterDelegate(auth, entitlement, FakeSession(), ownerScope)
        presenter.signIn("user@example.test", "password")
        runCurrent()
        ownerJob.cancel()
        runCurrent()
        assertTrue(actionCancelled)
        assertFalse(presenter.state.value.loading)
        assertNull(presenter.state.value.errorCode)
    }

    @Test
    fun signInStaysLoadingUntilAccountDataIsSettled() = runTest {
        val session = FakeSession(settleImmediately = false)
        val presenter = AuthPresenterDelegate(FakeAuth(), entitlement, session, backgroundScope)
        presenter.signIn("user@example.test", "password")
        runCurrent()
        assertEquals(listOf(user.uid), session.signedIn)
        assertTrue(presenter.state.value.loading)

        session.state.value = AccountDataState.Idle
        runCurrent()
        assertFalse(presenter.state.value.loading)
    }

    @Test
    fun guestChoiceAndRetryAreForwardedToSession() = runTest {
        val session = FakeSession()
        val presenter = AuthPresenterDelegate(FakeAuth(), entitlement, session, backgroundScope)
        session.state.value = AccountDataState.NeedsGuestChoice(user.uid)
        runCurrent()
        assertEquals(AccountDataState.NeedsGuestChoice(user.uid), presenter.state.value.accountData)
        assertFalse(presenter.state.value.loading)

        presenter.resolveGuestChoice(GuestDataChoice.KEEP_ACCOUNT_DATA)
        presenter.retryAccountData()
        assertEquals(listOf(GuestDataChoice.KEEP_ACCOUNT_DATA), session.choices)
        assertEquals(1, session.retries)
    }

    @Test
    fun signOutGoesThroughSessionAndConfirmationForcesIt() = runTest {
        val session = FakeSession()
        val presenter = AuthPresenterDelegate(FakeAuth(), entitlement, session, backgroundScope)
        presenter.signOut()
        presenter.confirmSignOut()
        presenter.dismissSignOutWarning()
        assertEquals(listOf(false, true), session.signOuts)
        assertEquals(1, session.dismissals)
    }

    private class FakeAuth : AuthRepository {
        override val authState = MutableStateFlow<AuthUser?>(null)
        var action: suspend () -> AuthUser = { user }
        var calls = 0

        override suspend fun signIn(email: String, password: String): AuthUser {
            calls++
            return action().also { authState.value = it }
        }
        override suspend fun signUp(email: String, password: String) = signIn(email, password)
        override suspend fun signInWithGoogle(idToken: String) = signIn("", "")
        override suspend fun signInWithApple(idToken: String, rawNonce: String) = signIn("", "")
        override suspend fun signOut() { authState.value = null }
        override suspend fun idToken(): String? = null
        override suspend fun idTokenFor(uid: String): String? = null
    }

    private class FakeSession(private val settleImmediately: Boolean = true) : AccountSession {
        override val state = MutableStateFlow<AccountDataState>(AccountDataState.Idle)
        override val localDataGeneration = MutableStateFlow(0)
        val signedIn = mutableListOf<String>()
        val choices = mutableListOf<GuestDataChoice>()
        val signOuts = mutableListOf<Boolean>()
        var retries = 0
        var dismissals = 0

        override fun onSignedIn(uid: String) {
            signedIn += uid
            if (!settleImmediately) state.value = AccountDataState.Syncing
        }
        override fun resolveGuestChoice(choice: GuestDataChoice) { choices += choice }
        override fun retry() { retries++ }
        override fun signOut(force: Boolean) { signOuts += force }
        override fun dismissSignOutWarning() { dismissals++ }
        override suspend fun reconcile() = Unit
        override suspend fun resetUserData() = Unit
    }

    private companion object {
        val user = AuthUser("test-user", "user@example.test")
        val entitlement = object : EntitlementRepository {
            override suspend fun fetchEntitlement() = DataResult.Success(EntitlementDto())
        }
    }
}

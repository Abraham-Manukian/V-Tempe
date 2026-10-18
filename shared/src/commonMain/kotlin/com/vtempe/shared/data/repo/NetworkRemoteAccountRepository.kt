package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.RemoteAccountRepository
import com.vtempe.shared.domain.util.DataResult
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withTimeoutOrNull

class NetworkRemoteAccountRepository(
    private val api: ApiClient,
    private val auth: AuthRepository
) : RemoteAccountRepository {

    override suspend fun deleteAccountData(uid: String): Boolean {
        // Pinned to [uid]'s own token, like sync uploads: a sign-in switch can't redirect the delete.
        val token = auth.idTokenFor(uid) ?: return false
        // Bounded: the account itself is deleted right after, and Firebase only allows that within
        // minutes of the sign-in checked before this call (see AuthRepository.isSignInRecent).
        val result = withTimeoutOrNull(DELETE_TIMEOUT_MS) { api.deleteNoContent("/me", bearerToken = token) }
        if (result == null) {
            Napier.d(tag = "Account", message = "server account deletion timed out")
            return false
        }
        if (result is DataResult.Failure) {
            Napier.d(tag = "Account", message = "server account deletion failed: ${result.reason} ${result.code ?: ""}")
        }
        return result is DataResult.Success
    }

    private companion object {
        const val DELETE_TIMEOUT_MS = 30_000L
    }
}

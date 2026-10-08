package com.vtempe.shared.data.repo

import com.vtempe.shared.data.network.ApiClient
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.RemoteAccountRepository
import io.github.aakira.napier.Napier

class NetworkRemoteAccountRepository(
    private val api: ApiClient,
    private val auth: AuthRepository
) : RemoteAccountRepository {

    override suspend fun deleteAccountData(uid: String): Boolean {
        // Pinned to [uid]'s own token, like sync uploads: a sign-in switch can't redirect the delete.
        val token = auth.idTokenFor(uid) ?: return false
        val ok = api.deleteNoContent("/me", bearerToken = token)
        if (!ok) Napier.d(tag = "Account", message = "server account deletion failed")
        return ok
    }
}

package com.vtempe.shared.domain.repository

/** The account's server-side data — `DELETE /me` on the server. */
interface RemoteAccountRepository {
    /** Erases everything the server holds for [uid] (idempotent). Returns false if that could not
     *  be confirmed — offline, server error, or [uid] is no longer the signed-in user. */
    suspend fun deleteAccountData(uid: String): Boolean
}

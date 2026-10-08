package com.vtempe.ui.presenter

import com.vtempe.shared.domain.repository.AuthErrorCode
import com.vtempe.shared.domain.repository.SignInMethod

sealed interface AccountDeletionUiState {
    data object Idle : AccountDeletionUiState
    data object InProgress : AccountDeletionUiState

    /** The user must sign in again with [method] before the deletion can be retried; [error] is
     *  why the last re-authentication attempt failed, if it did. */
    data class NeedsReauthentication(
        val method: SignInMethod?,
        val error: AuthErrorCode? = null
    ) : AccountDeletionUiState

    /** The account still exists; the user can simply try again. */
    data object Failed : AccountDeletionUiState
}

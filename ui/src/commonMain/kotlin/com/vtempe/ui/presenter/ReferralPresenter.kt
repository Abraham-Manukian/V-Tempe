package com.vtempe.ui.presenter

import com.vtempe.shared.domain.referral.AttachCodeResult
import com.vtempe.shared.domain.referral.ReferralCodeResult
import com.vtempe.shared.domain.referral.ReferralOverview
import com.vtempe.shared.domain.referral.ReferralRepository
import com.vtempe.shared.domain.util.DataResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Everything the invite screen shows; the rules and amounts come from the server. */
data class ReferralUiState(
    val loading: Boolean = false,
    val overview: ReferralOverview? = null,
    /** The server answered 401: the user has to sign in first. */
    val signInRequired: Boolean = false,
    val loadFailed: Boolean = false,
    val requestingCode: Boolean = false,
    /** The last code request failed because the user is not subscribed. */
    val subscriptionRequired: Boolean = false,
    val codeRequestFailed: Boolean = false,
    val codeInput: String = "",
    val attaching: Boolean = false,
    val attachResult: AttachCodeResult? = null,
)

interface ReferralPresenter {
    val state: StateFlow<ReferralUiState>
    fun refresh()
    fun requestCode()
    fun onCodeInput(value: String)
    fun attach()
}

/** Shared by Android and iOS (ViewModel / iOS presenter wrap it with their own scope). */
class ReferralPresenterDelegate(
    private val repository: ReferralRepository,
    private val scope: CoroutineScope,
) : ReferralPresenter {
    private val _state = MutableStateFlow(ReferralUiState())
    /** A refresh asked for while one was running (e.g. right after a code was applied). */
    private var reloadRequested = false
    override val state: StateFlow<ReferralUiState> = _state.asStateFlow()

    override fun refresh() {
        if (_state.value.loading) {
            // The running load may have started before the change it should show: load again after it.
            reloadRequested = true
            return
        }
        _state.update { it.copy(loading = true, loadFailed = false) }
        scope.launch {
            when (val result = repository.overview()) {
                is DataResult.Success -> _state.update {
                    it.copy(
                        loading = false, overview = result.data, signInRequired = false, loadFailed = false,
                        // Fresh server data replaces messages about an older state (e.g. "subscribers
                        // only" from before the user subscribed).
                        subscriptionRequired = false, codeRequestFailed = false,
                        attachResult = it.attachResult.takeUnless { r -> r == AttachCodeResult.SIGN_IN_REQUIRED },
                    )
                }
                is DataResult.Failure -> _state.update {
                    it.copy(loading = false, signInRequired = result.code == 401, loadFailed = result.code != 401)
                }
            }
            if (reloadRequested) {
                reloadRequested = false
                refresh()
            }
        }
    }

    override fun requestCode() {
        if (_state.value.requestingCode) return
        _state.update { it.copy(requestingCode = true, subscriptionRequired = false, codeRequestFailed = false) }
        scope.launch {
            when (val result = repository.requestCode()) {
                is ReferralCodeResult.Issued -> {
                    _state.update { current ->
                        current.copy(requestingCode = false, overview = current.overview?.copy(code = result.code))
                    }
                    if (_state.value.overview == null) refresh()
                }
                ReferralCodeResult.SubscriptionRequired -> _state.update { it.copy(requestingCode = false, subscriptionRequired = true) }
                is ReferralCodeResult.Failed -> _state.update {
                    it.copy(requestingCode = false, codeRequestFailed = true, signInRequired = it.signInRequired || result.failure.code == 401)
                }
            }
        }
    }

    override fun onCodeInput(value: String) {
        // Codes are 8 characters; extra room for the dash or spaces people paste.
        _state.update { it.copy(codeInput = value.take(MAX_CODE_INPUT), attachResult = null) }
    }

    override fun attach() {
        val code = _state.value.codeInput.trim()
        if (code.isEmpty() || _state.value.attaching) return
        _state.update { it.copy(attaching = true, attachResult = null) }
        scope.launch {
            val result = repository.attach(code)
            _state.update {
                it.copy(
                    attaching = false,
                    attachResult = result,
                    codeInput = if (result == AttachCodeResult.ATTACHED) "" else it.codeInput,
                    signInRequired = it.signInRequired || result == AttachCodeResult.SIGN_IN_REQUIRED,
                )
            }
            if (result == AttachCodeResult.ATTACHED) refresh()
        }
    }

    private companion object {
        const val MAX_CODE_INPUT = 16
    }
}

package com.vtempe.ui.presenter

import com.vtempe.shared.domain.account.AccountDeletionOutcome
import com.vtempe.shared.domain.account.AccountSession
import com.vtempe.shared.domain.model.AiModelMode
import com.vtempe.shared.domain.model.Profile
import com.vtempe.shared.domain.repository.AuthException
import com.vtempe.shared.domain.repository.AuthRepository
import com.vtempe.shared.domain.repository.AuthUser
import com.vtempe.shared.domain.repository.PreferencesRepository
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.repository.ReauthCredential
import com.vtempe.shared.domain.usecase.EnsureCoachData
import com.vtempe.shared.domain.usecase.SyncAnalyticsProfile
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsState(
    val profile: Profile? = null,
    val saving: Boolean = false,
    val aiModelMode: AiModelMode = AiModelMode.PAID,
    val analyticsConsent: Boolean = false,
    val authUser: AuthUser? = null,
    val resetting: Boolean = false,
    val deletion: AccountDeletionUiState = AccountDeletionUiState.Idle
)

interface SettingsPresenter {
    val state: StateFlow<SettingsState>
    fun refresh()
    fun save(profile: Profile)
    fun reset(onDone: () -> Unit)
    /** Deletes the account on the server, the account itself and all device data; [onDone] runs
     *  only after a complete deletion. */
    fun deleteAccount(onDone: () -> Unit)
    /** Answers [AccountDeletionUiState.NeedsReauthentication]: signs in again, then retries. */
    fun reauthenticateAndDeleteAccount(credential: ReauthCredential, onDone: () -> Unit)
    fun dismissAccountDeletion()
    fun setUnits(units: String)
    fun setLanguage(tag: String?)
    fun setAiModelMode(mode: AiModelMode)
    fun setAnalyticsConsent(granted: Boolean)
}

class SettingsPresenterDelegate(
    private val profileRepository: ProfileRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ensureCoachData: EnsureCoachData,
    private val accountSession: AccountSession,
    private val syncAnalyticsProfile: SyncAnalyticsProfile,
    private val authRepository: AuthRepository,
    private val scope: CoroutineScope,
    /** Platform hook: Android calls AppCompatDelegate, iOS is no-op. */
    private val applyLocale: (tag: String?) -> Unit = {}
) : SettingsPresenter {

    private val _state = MutableStateFlow(SettingsState())
    override val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { refresh() }

    override fun refresh() {
        scope.launch {
            val profile = runCatching { profileRepository.getProfile() }.getOrNull()
            val mode = runCatching { preferencesRepository.getAiModelMode() }.getOrDefault(AiModelMode.PAID)
            val consent = runCatching { preferencesRepository.getAnalyticsConsent() }.getOrDefault(false)
            val authUser = authRepository.authState.value
            _state.update { it.copy(profile = profile, aiModelMode = mode, analyticsConsent = consent, authUser = authUser) }
        }
    }

    override fun save(profile: Profile) {
        _state.update { it.copy(saving = true) }
        scope.launch {
            runCatching {
                profileRepository.upsertProfile(profile)
                ensureCoachData(force = true)
                syncAnalyticsProfile(profile) // no-op internally unless consent is already granted
            }.onFailure { Napier.e("Settings save failed", it) }
            _state.update { it.copy(profile = profile, saving = false) }
        }
    }

    override fun reset(onDone: () -> Unit) {
        if (_state.value.resetting) return
        _state.update { it.copy(resetting = true) }
        scope.launch {
            // Everything the user created: profile, plans, progress, sleep, weight, chat, AI cache,
            // and the signed-in account's synced copy. Device preferences (language, units) stay.
            try {
                accountSession.resetUserData()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.e("Reset failed", error)
            }
            _state.value = SettingsState()
            onDone()
        }
    }

    override fun deleteAccount(onDone: () -> Unit) =
        runDeletion(onDone) { accountSession.deleteAccount() }

    override fun reauthenticateAndDeleteAccount(credential: ReauthCredential, onDone: () -> Unit) =
        runDeletion(onDone) {
            authRepository.reauthenticate(credential)
            accountSession.deleteAccount()
        }

    override fun dismissAccountDeletion() {
        if (_state.value.deletion != AccountDeletionUiState.InProgress) {
            _state.update { it.copy(deletion = AccountDeletionUiState.Idle) }
        }
    }

    private fun runDeletion(onDone: () -> Unit, step: suspend () -> AccountDeletionOutcome) {
        if (_state.value.deletion == AccountDeletionUiState.InProgress) return
        _state.update { it.copy(deletion = AccountDeletionUiState.InProgress) }
        scope.launch {
            val next = try {
                when (step()) {
                    AccountDeletionOutcome.Deleted -> {
                        _state.value = SettingsState()
                        onDone()
                        return@launch
                    }
                    AccountDeletionOutcome.NeedsReauthentication -> reauthenticationNeeded(error = null)
                    AccountDeletionOutcome.Failed -> AccountDeletionUiState.Failed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (rejected: AuthException) {
                // Only re-authentication throws this: let the user try signing in again.
                reauthenticationNeeded(rejected)
            } catch (error: Exception) {
                Napier.e("Account deletion failed", error)
                AccountDeletionUiState.Failed
            }
            _state.update { it.copy(deletion = next) }
        }
    }

    private fun reauthenticationNeeded(error: AuthException?) = AccountDeletionUiState.NeedsReauthentication(
        method = authRepository.authState.value?.signInMethod,
        error = error?.code
    )

    override fun setUnits(units: String) {
        preferencesRepository.setUnits(units)
    }

    override fun setLanguage(tag: String?) {
        preferencesRepository.setLanguageTag(tag)
        applyLocale(tag)
    }

    override fun setAiModelMode(mode: AiModelMode) {
        preferencesRepository.setAiModelMode(mode)
        _state.update { it.copy(aiModelMode = mode) }
    }

    override fun setAnalyticsConsent(granted: Boolean) {
        preferencesRepository.setAnalyticsConsent(granted)
        _state.update { it.copy(analyticsConsent = granted) }
        // Send the bucketed demographic snapshot immediately on opt-in so it's not
        // waiting on the next profile save. Opting out only stops FUTURE syncs —
        // Firebase has no API to retroactively delete already-sent user properties.
        if (granted) {
            _state.value.profile?.let { syncAnalyticsProfile(it) }
        }
    }
}

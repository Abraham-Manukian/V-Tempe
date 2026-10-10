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

/** Where reading the stored profile stands (VT-AUD-070): null alone could not tell these apart. */
enum class ProfileLoad { LOADING, READY, MISSING, FAILED }

data class SettingsState(
    val profile: Profile? = null,
    val profileLoad: ProfileLoad = ProfileLoad.LOADING,
    val saving: Boolean = false,
    /** The last save did not reach storage; the editor keeps the draft (VT-AUD-063). */
    val saveFailed: Boolean = false,
    /** The profile was saved, but the plans could not be rebuilt for it yet. */
    val planUpdateFailed: Boolean = false,
    /** The plans failed because the new restrictions leave no safe meal or exercise. */
    val planRestrictionConflict: Boolean = false,
    val aiModelMode: AiModelMode = AiModelMode.PAID,
    val analyticsConsent: Boolean = false,
    val authUser: AuthUser? = null,
    val resetting: Boolean = false,
    /** The last reset did not finish; some data may remain and the user can try again. */
    val resetFailed: Boolean = false,
    val deletion: AccountDeletionUiState = AccountDeletionUiState.Idle
)

interface SettingsPresenter {
    val state: StateFlow<SettingsState>
    fun refresh()
    /** Stores [profile]; [onSaved] runs only once it is durably saved. The plans are then rebuilt
     *  in the background — a failure there shows as [SettingsState.planUpdateFailed]. */
    fun save(profile: Profile, onSaved: () -> Unit = {})
    /** Rebuilds the plans for the saved profile after [SettingsState.planUpdateFailed]. */
    fun retryPlanUpdate()
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
        if (_state.value.profileLoad == ProfileLoad.FAILED) _state.update { it.copy(profileLoad = ProfileLoad.LOADING) }
        scope.launch {
            val read = runCatching { profileRepository.getProfile() }
                .onFailure { if (it is CancellationException) throw it; Napier.e("Profile read failed", it) }
            val mode = runCatching { preferencesRepository.getAiModelMode() }.getOrDefault(AiModelMode.PAID)
            val consent = runCatching { preferencesRepository.getAnalyticsConsent() }.getOrDefault(false)
            val authUser = authRepository.authState.value
            _state.update {
                it.copy(
                    // A failed read keeps whatever was shown before; it is never "no profile".
                    profile = read.getOrElse { _ -> it.profile },
                    profileLoad = when {
                        read.isFailure -> ProfileLoad.FAILED
                        read.getOrNull() == null -> ProfileLoad.MISSING
                        else -> ProfileLoad.READY
                    },
                    aiModelMode = mode,
                    analyticsConsent = consent,
                    authUser = authUser,
                )
            }
        }
    }

    override fun save(profile: Profile, onSaved: () -> Unit) {
        if (_state.value.saving) return
        _state.update { it.copy(saving = true, saveFailed = false) }
        scope.launch {
            try {
                profileRepository.upsertProfile(profile)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Nothing was stored: keep showing the stored profile, the editor keeps its draft.
                Napier.e("Profile save failed", error)
                _state.update { it.copy(saving = false, saveFailed = true) }
                return@launch
            }
            _state.update { it.copy(profile = profile, profileLoad = ProfileLoad.READY, saving = false) }
            onSaved()
            runCatching { syncAnalyticsProfile(profile) } // no-op internally unless consent is already granted
                .onFailure { if (it is CancellationException) throw it }
            rebuildPlans()
        }
    }

    override fun retryPlanUpdate() {
        scope.launch { rebuildPlans() }
    }

    private suspend fun rebuildPlans() {
        _state.update { it.copy(planUpdateFailed = false, planRestrictionConflict = false) }
        var conflict = false
        val rebuilt = try {
            ensureCoachData(force = true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Napier.e("Plan rebuild after profile save failed", error)
            conflict = error.isPlanRestrictionConflict()
            false
        }
        if (!rebuilt) _state.update { it.copy(planUpdateFailed = true, planRestrictionConflict = conflict) }
    }

    override fun reset(onDone: () -> Unit) {
        if (_state.value.resetting) return
        _state.update { it.copy(resetting = true, resetFailed = false) }
        scope.launch {
            // Everything the user created: profile, plans, progress, sleep, weight, chat, AI cache,
            // and the signed-in account's synced copy. Device preferences (language, units) stay.
            try {
                accountSession.resetUserData()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Napier.e("Reset failed", error)
                // Not a success: no restart, the user sees it failed and can retry (VT-AUD-061).
                _state.update { it.copy(resetting = false, resetFailed = true) }
                return@launch
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

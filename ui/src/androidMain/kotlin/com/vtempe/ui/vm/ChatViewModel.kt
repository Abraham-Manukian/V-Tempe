package com.vtempe.ui.vm

import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vtempe.shared.data.repo.ChatHistoryStore
import com.vtempe.shared.domain.repository.LanguagePreferences
import com.vtempe.shared.domain.repository.ProfileRepository
import com.vtempe.shared.domain.usecase.AskAiTrainer
import com.vtempe.ui.presenter.ChatPresenter
import com.vtempe.ui.presenter.ChatPresenterDelegate
import com.vtempe.ui.presenter.ChatState
import kotlinx.coroutines.flow.StateFlow

class ChatViewModel(
    ask: AskAiTrainer,
    languagePrefs: LanguagePreferences,
    profileRepository: ProfileRepository,
    chatHistoryStore: ChatHistoryStore,
) : ViewModel(), ChatPresenter {

    private val delegate = ChatPresenterDelegate(
        ask = ask,
        profileRepository = profileRepository,
        chatHistoryStore = chatHistoryStore,
        scope = viewModelScope,
        localeProvider = {
            languagePrefs.getLanguageTag()
                ?: LocaleListCompat.getAdjustedDefault().get(0)?.toLanguageTag()
        }
    )

    override val state: StateFlow<ChatState> get() = delegate.state
    override fun updateInput(text: String) = delegate.updateInput(text)
    override fun send() = delegate.send()
}

package com.vtempe.ui.platform.voice

import androidx.compose.runtime.Composable

enum class MicrophonePermissionResult {
    GRANTED,
    /** Denied this time; the system may ask again next time. */
    DENIED,
    /** Denied for good: only the system settings can turn it back on. */
    BLOCKED
}

/** Platform permission flow for dictation (the microphone, plus speech recognition on iOS). */
interface MicrophoneAccess {
    /** Asks the system when needed; answers at once when the decision is already known. */
    fun request(onResult: (MicrophonePermissionResult) -> Unit)

    /** Opens this app's page in the system settings. */
    fun openSettings()
}

@Composable
expect fun rememberMicrophoneAccess(): MicrophoneAccess

/** Calls [onInterrupted] when the app goes to the background, so no recording runs unseen. */
@Composable
expect fun OnAppBackgrounded(onInterrupted: () -> Unit)

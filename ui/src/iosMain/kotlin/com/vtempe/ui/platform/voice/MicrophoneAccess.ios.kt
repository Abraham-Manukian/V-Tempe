package com.vtempe.ui.platform.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.AVFAudio.AVAudioSession
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Speech.SFSpeechRecognizer
import platform.Speech.SFSpeechRecognizerAuthorizationStatus
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Dictation needs two iOS permissions: the microphone and speech recognition. iOS asks for each
 * only once; after a denial both answer "no" at once, so a denial is [MicrophonePermissionResult.BLOCKED]
 * (only Settings can change it). Not compiled on the Windows dev machine; verify on macOS.
 */
private class IosMicrophoneAccess : MicrophoneAccess {
    override fun request(onResult: (MicrophonePermissionResult) -> Unit) {
        val deliver: (MicrophonePermissionResult) -> Unit = { result ->
            dispatch_async(dispatch_get_main_queue()) { onResult(result) }
        }
        AVAudioSession.sharedInstance().requestRecordPermission { micGranted ->
            if (!micGranted) {
                deliver(MicrophonePermissionResult.BLOCKED)
            } else {
                SFSpeechRecognizer.requestAuthorization { status ->
                    deliver(
                        if (status == SFSpeechRecognizerAuthorizationStatus.SFSpeechRecognizerAuthorizationStatusAuthorized) {
                            MicrophonePermissionResult.GRANTED
                        } else {
                            MicrophonePermissionResult.BLOCKED
                        }
                    )
                }
            }
        }
    }

    override fun openSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

@Composable
actual fun rememberMicrophoneAccess(): MicrophoneAccess = remember { IosMicrophoneAccess() }

@Composable
actual fun OnAppBackgrounded(onInterrupted: () -> Unit) {
    val callback = rememberUpdatedState(onInterrupted)
    DisposableEffect(Unit) {
        // Not "will resign active": the permission alerts themselves resign the app's active state.
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ -> callback.value() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
}

package com.vtempe.ui.platform.voice

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

private class AndroidMicrophoneAccess(private val context: Context) : MicrophoneAccess {
    /** Set by [rememberMicrophoneAccess] once the system permission launcher exists. */
    var launch: (() -> Unit)? = null
    private var pending: ((MicrophonePermissionResult) -> Unit)? = null

    override fun request(onResult: (MicrophonePermissionResult) -> Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            onResult(MicrophonePermissionResult.GRANTED)
            return
        }
        val launcher = launch
        if (launcher == null) {
            onResult(MicrophonePermissionResult.DENIED)
        } else {
            pending = onResult
            launcher()
        }
    }

    fun onSystemAnswer(granted: Boolean) {
        val callback = pending ?: return
        pending = null
        callback(
            when {
                granted -> MicrophonePermissionResult.GRANTED
                isPermanentlyDenied() -> MicrophonePermissionResult.BLOCKED
                else -> MicrophonePermissionResult.DENIED
            }
        )
    }

    // After a denial the system stops offering the rationale once it will no longer show the
    // dialog; only the app settings page can turn the microphone back on. A first dialog closed
    // without an answer looks the same, and the settings page also works for that user.
    private fun isPermanentlyDenied(): Boolean {
        val activity = context.findActivity() ?: return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
    }

    override fun openSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

@Composable
actual fun rememberMicrophoneAccess(): MicrophoneAccess {
    val context = LocalContext.current
    val access = remember(context) { AndroidMicrophoneAccess(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        access.onSystemAnswer(granted)
    }
    SideEffect { access.launch = { launcher.launch(Manifest.permission.RECORD_AUDIO) } }
    return access
}

@Composable
actual fun OnAppBackgrounded(onInterrupted: () -> Unit) {
    val owner = LocalContext.current.findActivity() as? LifecycleOwner ?: return
    val callback = rememberUpdatedState(onInterrupted)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) callback.value()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

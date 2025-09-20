package org.avium.systemui.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Process
import android.util.Log
import com.android.systemui.dagger.SysUISingleton
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.avium.systemui.lockscreen.util.CustomLockscreenSettings

private const val TAG = "AVIUM_LOCKSCREEN"


@SysUISingleton
class CustomLockscreenRepository @Inject constructor(
    private val context: Context
) {
    private val _isEnabled = MutableStateFlow(CustomLockscreenSettings.isEnabled())
    val isEnabled = _isEnabled.asStateFlow()

    private val settingsChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_SETTINGS_CHANGED) {
                Log.d(TAG, "Received settings changed broadcast, restarting SystemUI")
                Process.killProcess(Process.myPid())
            }
        }
    }

    init {
        val filter = IntentFilter(ACTION_SETTINGS_CHANGED)
        context.registerReceiver(settingsChangedReceiver, filter, Context.RECEIVER_EXPORTED)
    }

    private fun updateState() {
        val newState = CustomLockscreenSettings.isEnabled()
        if (_isEnabled.value != newState) {
            _isEnabled.value = newState
        }
    }

    companion object {
        const val ACTION_SETTINGS_CHANGED = "org.avium.systemui.lockscreen.SETTINGS_CHANGED"
    }
}
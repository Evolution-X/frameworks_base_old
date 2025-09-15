/*
 * Copyright (C) 2025 AxionOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.android.systemui.statusbar.policy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Handler
import android.util.Log
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.phone.SystemUIDialog
import javax.inject.Inject
import javax.inject.Provider

@SysUISingleton
class VolumeController @Inject constructor(
    private val ctx: Context,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val dialogDelegateProvider: Provider<VolumeDialogDelegate>,
    private val keyguardStateController: KeyguardStateController,
    private val activityStarter: ActivityStarter,
    private val mainHandler: Handler
) {

    interface VolumeListener {
        fun onVolumeChanged(volume: Int)
        fun onMuteStateChanged(muted: Boolean)
    }

    fun interface OnDialogDismissedListener {
        fun onDismiss()
    }

    private val am: AudioManager = 
        ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val listeners = mutableSetOf<VolumeListener>()
    
    private val volumeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.VOLUME_CHANGED_ACTION -> {
                    val streamType = intent.getIntExtraValue(
                        AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1
                    )
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        notifyVolumeChanged()
                    }
                }
                AudioManager.STREAM_MUTE_CHANGED_ACTION -> {
                    val streamType = intent.getIntExtraValue(
                        AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1
                    )
                    if (streamType == AudioManager.STREAM_MUSIC) {
                        notifyMuteStateChanged()
                    }
                }
            }
        }
    }

    val max get() = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val current get() = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    val percent get() = if (max > 0) (current * 100f / max).toInt() else 0

    fun addListener(listener: VolumeListener) {
        synchronized(listeners) {
            val wasEmpty = listeners.isEmpty()
            listeners.add(listener)
            if (wasEmpty) {
                registerVolumeReceiver()
            }
        }
    }

    fun removeListener(listener: VolumeListener) {
        synchronized(listeners) {
            listeners.remove(listener)
            if (listeners.isEmpty()) {
                unregisterVolumeReceiver()
            }
        }
    }

    private fun registerVolumeReceiver() {
        val filter = IntentFilter().apply {
            addAction(AudioManager.VOLUME_CHANGED_ACTION)
            addAction(AudioManager.STREAM_MUTE_CHANGED_ACTION)
        }
        ctx.registerReceiver(volumeChangeReceiver, filter)
    }

    private fun unregisterVolumeReceiver() {
        try {
            ctx.unregisterReceiver(volumeChangeReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Volume receiver not registered", e)
        }
    }

    private fun notifyVolumeChanged() {
        synchronized(listeners) {
            listeners.forEach { it.onVolumeChanged(percent) }
        }
    }

    private fun notifyMuteStateChanged() {
        val muted = am.isStreamMute(AudioManager.STREAM_MUSIC)
        synchronized(listeners) {
            listeners.forEach { it.onMuteStateChanged(muted) }
        }
    }

    fun toVolume(p: Int) {
        val vol = (p.coerceIn(0, 100) * max / 100f).toInt().coerceIn(0, max)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, vol, 0)
    }

    fun expandDialog(expandable: Expandable?) {
        val animateFromExpandable = expandable != null && !keyguardStateController.isShowing
        val runnable = Runnable {
            val delegate = dialogDelegateProvider.get()
            val dialog: SystemUIDialog = delegate.createDialog()
            if (animateFromExpandable) {
                val controller = expandable?.dialogTransitionController(
                    DialogCuj(
                        InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                        "volume_control"
                    )
                )
                if (controller != null) {
                    dialogTransitionAnimator.show(dialog, controller)
                } else {
                    dialog.show()
                }
            } else {
                dialog.show()
            }
        }
        mainHandler.post {
            activityStarter.executeRunnableDismissingKeyguard(
                runnable, null, false, true, false
            )
        }
    }

    private fun Intent.getIntExtraValue(name: String, defaultValue: Int): Int {
        return getIntExtra(name, defaultValue)
    }
    
    private companion object {
        private const val TAG = "VolumeController"
    }
}

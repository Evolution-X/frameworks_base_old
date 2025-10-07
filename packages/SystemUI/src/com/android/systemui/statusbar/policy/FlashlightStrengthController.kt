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

import android.annotation.WorkerThread
import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.SystemProperties
import android.provider.Settings
import android.util.Log
import com.android.internal.jank.InteractionJankMonitor
import com.android.systemui.Prefs
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.statusbar.phone.SystemUIDialog
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Provider
import kotlin.math.roundToInt

@SysUISingleton
class FlashlightStrengthController @Inject constructor(
    private val ctx: Context,
    private val executor: Executor,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
    private val dialogDelegateProvider: Provider<FlashlightStrengthDialogDelegate>,
    private val keyguardStateController: KeyguardStateController,
    private val activityStarter: ActivityStarter,
    private val mainHandler: Handler
) {

    interface OnTorchLevelChangedListener {
        fun onLevelChanged(level: Int)
        fun onStatusChanged(enabled: Int)
    }

    private val featureSupport =
        SystemProperties.getBoolean("persist.sys.torch_str_support", true)

    private val cm: CameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchId: String? = null
    private val listeners = mutableSetOf<OnTorchLevelChangedListener>()

    private val _torchOn: Boolean
        get() = Settings.Secure.getInt(
            ctx.contentResolver,
            Settings.Secure.FLASHLIGHT_ENABLED, 0
        ) != 0

    var torchOn: Boolean
        get() = _torchOn
        set(value) {
            if (value == torchOn) return
            executor.execute {
                torchId?.let { id ->
                    runCatching {
                        cm.setTorchMode(id, value)
                        _notifyStatusChanged(if (value) 1 else 0)
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to set torch mode: $e")
                        _notifyStatusChanged(if (_torchOn) 1 else 0)
                    }
                }
            }
        }

    private var _torchLevel = 0
    var torchLevel: Int
        get() = if (torchOn) _torchLevel else 0
        set(value) {
            val lvl = value.coerceIn(0, maxLevel)
            executor.execute {
                torchId?.let { id ->
                    if (lvl == 0) {
                        runCatching { cm.setTorchMode(id, false) }
                            .onSuccess {
                                _torchLevel = 0
                                _notifyLevelChanged(0)
                                _notifyStatusChanged(0)
                            }
                    } else {
                        runCatching {
                            cm.turnOnTorchWithStrengthLevel(id, lvl)
                            _torchLevel = lvl
                            _notifyLevelChanged(lvl)
                            _notifyStatusChanged(1)
                        }.onFailure { e ->
                            Log.w(TAG, "Failed to change torch level: $e")
                            _torchLevel = 0
                            _notifyLevelChanged(0)
                            _notifyStatusChanged(0)
                        }
                    }
                }
            }
        }

    private var _supported = false
    val supported: Boolean by lazy {
        try {
            for (id in cm.cameraIdList) {
                val c = cm.getCameraCharacteristics(id)
                val flashAvailable = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facingBack =
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                val max = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1

                if (flashAvailable && facingBack && max > 1 && featureSupport) {
                    torchId = id
                    _maxLevel = max
                    Log.d(TAG, "Flashlight strength supported, max level=$max")
                    return@lazy true
                }
            }
            Log.d(TAG, "Flashlight strength not supported on any camera.")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize flashlight camera: $e")
            false
        }
    }

    private var _maxLevel = 1
    val maxLevel get() = _maxLevel

    var lastPercent: Int
        get() = Prefs.getInt(ctx, PREF_KEY, 0)
        set(value) = Prefs.putInt(ctx, PREF_KEY, value.coerceIn(0, 100))

    fun handleClick() {
        torchLevel = when {
            torchOn -> 0
            lastPercent == 0 -> 1
            else -> toTorchLevel(lastPercent)
        }
    }

    fun toTorchLevel(percent: Int): Int =
        ((percent / 100f) * maxLevel).roundToInt().coerceIn(0, maxLevel)

    fun toPercent(level: Int): Int =
        (level * 100f / maxLevel).roundToInt()

    fun expandDialog(expandable: Expandable?) {
        val animateFromExpandable = expandable != null && !keyguardStateController.isShowing
        val runnable = Runnable {
            val delegate = dialogDelegateProvider.get()
            val dialog = delegate.createDialog()
            if (animateFromExpandable) {
                expandable?.dialogTransitionController(
                    DialogCuj(
                        InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN,
                        "flashlight_strength"
                    )
                )?.let { controller ->
                    dialogTransitionAnimator.show(dialog, controller)
                } ?: dialog.show()
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

    fun addListener(listener: OnTorchLevelChangedListener) {
        synchronized(listeners) {
            listeners.add(listener)
        }
        listener.onLevelChanged(_torchLevel)
        listener.onStatusChanged(if (torchOn) 1 else 0)
    }

    fun removeListener(listener: OnTorchLevelChangedListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun _notifyLevelChanged(level: Int) {
        mainHandler.post {
            synchronized(listeners) {
                listeners.forEach { it.onLevelChanged(level) }
            }
        }
    }

    private fun _notifyStatusChanged(enabled: Int) {
        mainHandler.post {
            synchronized(listeners) {
                listeners.forEach { it.onStatusChanged(enabled) }
            }
        }
    }

    companion object {
        private const val TAG = "FlashlightStrengthCtl"
        private const val PREF_KEY = "flashlight_strength_percent"
    }
}

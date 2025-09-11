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
import javax.inject.Inject
import javax.inject.Provider
import java.util.concurrent.Executor
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

    fun interface OnTorchLevelChangedListener {
        fun onLevelChanged(level: Int)
    }

    private val cm: CameraManager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var torchId: String? = null
    var listener: OnTorchLevelChangedListener? = null
        set(value) {
            field = value
            value?.onLevelChanged(_torchLevel)
        }

    private val _torchOn
        get() = Settings.Secure.getInt(
            ctx.contentResolver,
            Settings.Secure.FLASHLIGHT_ENABLED, 0) != 0

    var torchOn: Boolean
        get() = _torchOn
        set(value) {
            if (value == torchOn) return
            executor.execute {
                torchId?.let { id ->
                    runCatching { cm.setTorchMode(id, value) }
                }
            }
        }

    private var _torchLevel = 0
    var torchLevel: Int
        get() = if (torchOn) _torchLevel else 0
        set(value) {
            val lvl = value.coerceIn(0, maxLevel)
            _torchLevel = lvl
            if (lvl == 0) torchOn = false
            else {
                torchOn = true
                executor.execute {
                    torchId?.let { id ->
                        runCatching {
                            cm.turnOnTorchWithStrengthLevel(id, lvl)
                        }.onFailure { _torchLevel = 0 }
                    }
                }
            }
            listener?.onLevelChanged(lvl)
        }

    private var _supported = false
    val supported get() = _supported

    private var _maxLevel = 1
    val maxLevel get() = _maxLevel

    var lastPercent: Int
        get() = Prefs.getInt(ctx, PREF_KEY, 0)
        set(value) = Prefs.putInt(ctx, PREF_KEY, value.coerceIn(0, 100))

    init { executor.execute { tryInitCamera() } }

    @WorkerThread
    fun tryInitCamera() {
        runCatching {
            for (id in cm.cameraIdList) {
                val c = cm.getCameraCharacteristics(id)
                val ok = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true 
                        && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                val max = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
                val def = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL) ?: 0
                if (ok && max > 1) {
                    torchId = id
                    _maxLevel = max
                    _supported = true
                    break
                }
            }
        }.onFailure { _supported = false }
    }

    fun handleClick() {
        torchLevel = when {
            torchOn -> 0
            lastPercent == 0 -> 1
            else -> toTorchLevel(lastPercent)
        }
    }

    fun toTorchLevel(lvl: Int): Int {
        return ((lvl / 100f) * maxLevel).roundToInt().coerceIn(0, maxLevel)
    }

    fun toPercent(lvl: Int): Int {
        return (lvl * 100f / maxLevel).roundToInt()
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
                        "flashlight_strength"
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

    companion object {
        private const val PREF_KEY = "flashlight_strength_percent"
    }
}

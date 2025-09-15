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

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import com.android.settingslib.Utils
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.util.concurrency.DelayableExecutor
import com.android.systemui.util.time.SystemClock
import com.google.android.material.slider.Slider
import javax.inject.Inject
import kotlin.math.roundToInt

class VolumeDialogDelegate @Inject constructor(
    private val ctx: Context,
    private val systemUIDialogFactory: SystemUIDialog.Factory,
    private val layoutInflater: android.view.LayoutInflater,
    private val ctl: VolumeController,
    private val systemClock: SystemClock,
    @Main private val mainHandler: Handler,
    @Background private val backgroundDelayableExecutor: DelayableExecutor
) : SystemUIDialog.Delegate, VolumeController.VolumeListener {

    companion object {
        private const val INTERVAL_MS: Long = 50
        private const val SLIDER_DELAY_MS: Long = 50
    }

    private lateinit var percentText: TextView
    private lateinit var doneButton: Button
    private lateinit var slider: Slider

    private var updateTime: Long = 0
    private var task: Runnable? = null
    private var muted = false
    private var userTracking = false

    private val muteText = string(R.string.qs_volume_dialog_mute)
    private val iconVolume: Drawable get() = ContextCompat.getDrawable(ctx, R.drawable.ic_volume_media)!!
    private val iconColor: ColorStateList
        get() = ColorStateList.valueOf(
            Utils.getColorAttrDefaultColor(ctx, android.R.attr.textColorPrimaryInverse)
        )

    private var percent: Int = 0
        set(value) {
            percentText.text = if (value == 0) muteText else "${value}%"
            field = value
        }

    private var volumePercent: Int = 0
        @MainThread set(value) {
            mainHandler.post {
                task?.run()
                val now = systemClock.elapsedRealtime()
                val delay = if (now - updateTime < INTERVAL_MS) SLIDER_DELAY_MS else 0L
                task = backgroundDelayableExecutor.executeDelayed({
                    ctl.toVolume(value)
                    field = value
                    updateTime = systemClock.elapsedRealtime()
                }, delay)
            }
        }
        
    private var progress = 0
        set(value) {
            slider.value = value.toFloat()
            percent = value
            field = value
        }

    override fun createDialog(): SystemUIDialog = systemUIDialogFactory.create(this)

    override fun beforeCreate(d: SystemUIDialog, b: Bundle?) {
        d.setTitle(R.string.qs_volume_dialog_title)
        d.setView(layoutInflater.inflate(R.layout.qs_volume_dialog, null))
        d.setPositiveButton(R.string.quick_settings_done, null, true)
    }

    override fun onCreate(d: SystemUIDialog, savedInstanceState: Bundle?) {
        percentText = d.requireViewById(R.id.volume_percentage_text)
        doneButton = d.requireViewById(com.android.internal.R.id.button1)
        slider = d.requireViewById(R.id.volume_slider)

        ctl.addListener(this)

        setupSlider()
        setupListeners(d)
    }

    private fun setupSlider() {
        slider.valueFrom = 0f
        slider.valueTo = 100f
        slider.stepSize = 1f
        progress = ctl.percent
        slider.trackIconActiveStart = iconVolume
        slider.trackIconActiveColor = iconColor
    }

    private fun setupListeners(d: SystemUIDialog) {
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val v = value.roundToInt()
                percent = v
                volumePercent = v
            }
        }
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                task?.run()
                userTracking = true
            }
            override fun onStopTrackingTouch(slider: Slider) {
                userTracking = false
                volumePercent = slider.value.roundToInt()
            }
        })
        doneButton.setOnClickListener { d.dismiss() }
    }

    override fun onStop(d: SystemUIDialog) {
        task?.run()
        task = null
        ctl.removeListener(this)
    }

    override fun onVolumeChanged(volume: Int) {
        if (!userTracking) {
            progress = volume
        }
    }

    override fun onMuteStateChanged(muted: Boolean) {
        this.muted = muted
        if (!userTracking) {
            progress = if (muted) 0 else ctl.percent
        }
    }

    private fun string(res: Int): String {
        return ctx.getString(res)
    }
}

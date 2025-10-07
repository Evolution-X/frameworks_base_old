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

class FlashlightStrengthDialogDelegate @Inject constructor(
    private val ctx: Context,
    private val systemUIDialogFactory: SystemUIDialog.Factory,
    private val layoutInflater: android.view.LayoutInflater,
    private val ctl: FlashlightStrengthController,
    private val systemClock: SystemClock,
    @Main private val mainHandler: Handler,
    @Background private val backgroundDelayableExecutor: DelayableExecutor
) : SystemUIDialog.Delegate, FlashlightStrengthController.OnTorchLevelChangedListener {

    companion object {
        private const val INTERVAL_MS: Long = 50
        private const val SLIDER_DELAY_MS: Long = 50
    }

    private lateinit var percentText: TextView
    private lateinit var doneButton: Button
    private lateinit var toggleButton: Button
    private lateinit var slider: Slider

    private var updateTime: Long = 0
    private var task: Runnable? = null

    private val offText = string(R.string.flashlight_strength_off)
    private val iconOn: Drawable get() = ContextCompat.getDrawable(ctx, R.drawable.qs_flashlight_icon_on)!!
    private val iconColor: ColorStateList
        get() = ColorStateList.valueOf(Utils.getColorAttrDefaultColor(ctx, android.R.attr.textColorPrimaryInverse))

    private var percent: Int = 0
        set(value) {
            field = value
            percentText.text = if (ctl.toTorchLevel(value) == 0) offText else "$value%"
        }

    private var torchStr: Int = 0
        set(value) {
            field = value
            mainHandler.post {
                task?.run()
                val now = systemClock.elapsedRealtime()
                val delay = if (now - updateTime < INTERVAL_MS) SLIDER_DELAY_MS else 0L
                task = backgroundDelayableExecutor.executeDelayed({
                    ctl.torchLevel = ctl.toTorchLevel(value)
                    updateTime = systemClock.elapsedRealtime()
                }, delay)
            }
        }

    override fun createDialog(): SystemUIDialog = systemUIDialogFactory.create(this)

    override fun beforeCreate(d: SystemUIDialog, b: Bundle?) {
        d.setTitle(R.string.flashlight_strength_dialog_title)
        d.setView(layoutInflater.inflate(R.layout.flashlight_strength_dialog, null))
        d.setPositiveButton(R.string.quick_settings_done, null, true)
        d.setNeutralButton(R.string.flashlight_turn_on, null, true)
    }

    override fun onCreate(d: SystemUIDialog, s: Bundle?) {
        percentText = d.requireViewById(R.id.flashlight_percentage_text)
        doneButton = d.requireViewById(com.android.internal.R.id.button1)
        toggleButton = d.requireViewById(com.android.internal.R.id.button3)
        slider = d.requireViewById(R.id.flashlight_strength_slider)
        setupSlider()
        setupListeners(d)
    }

    override fun onStart(d: SystemUIDialog) {
        ctl.addListener(this)
        updateToggleBtn()
        val pct = ctl.lastPercent
        slider.value = pct.toFloat()
        percent = pct
    }

    override fun onStop(d: SystemUIDialog) {
        task?.run()
        task = null
        ctl.removeListener(this)
        dispose()
    }

    private fun setupSlider() {
        slider.apply {
            valueFrom = 0f
            valueTo = 100f
            stepSize = 1f
            val pct = ctl.lastPercent
            value = pct.toFloat()
        }
        percent = ctl.lastPercent
        slider.trackIconActiveStart = iconOn
        slider.trackIconActiveColor = iconColor
    }

    private fun setupListeners(d: SystemUIDialog) {
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val v = value.roundToInt()
                percent = v
                torchStr = v
            }
        }

        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                task?.run()
            }

            override fun onStopTrackingTouch(slider: Slider) {
                val v = slider.value.roundToInt()
                ctl.lastPercent = v
                torchStr = v
            }
        })

        doneButton.setOnClickListener { d.dismiss() }
        toggleButton.setOnClickListener {
            val target = !ctl.torchOn
            toggleButton.isEnabled = false
            toggleButton.alpha = 0.6f
            ctl.torchOn = target
        }
    }

    private fun dispose() {
        slider.clearOnChangeListeners()
        slider.clearOnSliderTouchListeners()
        doneButton.setOnClickListener(null)
        toggleButton.setOnClickListener(null)
    }

    override fun onStatusChanged(enabled: Int) {
        toggleButton.isEnabled = true
        toggleButton.alpha = 1f
        updateToggleBtn()
    }

    override fun onLevelChanged(level: Int) {
        val newPercent = ctl.toPercent(level)
        if (newPercent != percent) {
            slider.value = newPercent.toFloat()
            percent = newPercent
        }
    }

    private fun updateToggleBtn() {
        val res = if (ctl.torchOn)
            R.string.flashlight_turn_off
        else
            R.string.flashlight_turn_on
        toggleButton.text = string(res)
    }

    private fun string(res: Int): String = ctx.getString(res)
}

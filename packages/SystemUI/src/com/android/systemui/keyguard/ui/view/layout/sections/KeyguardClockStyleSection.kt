/*
 * Copyright (C) 2025 the RisingOS Revived Android Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.os.UserHandle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.clocks.ClockStyle
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.util.settings.SecureSettings
import org.avium.systemui.lockscreen.CustomLockscreenClockManager
import javax.inject.Inject

private const val TAG = "KeyguardClockStyleSection"

/**
 * Handles RisingOS ClockStyle (1-17) and Avium clock styles (24-27)
 * 
 * Clock Style Values:
 * - 0: Default system clock (disabled)
 * - 1-16: RisingOS ClockStyle layouts
 * - 17-23: Handled by CustomClockSection
 * - 24: Avium NType Clock
 * - 25: Avium NDot Clock
 * - 26: Avium Graphic Clock
 * - 27: Avium LondonUG Clock
 */
class KeyguardClockStyleSection
@Inject
constructor(
    private val context: Context,
    private val secureSettings: SecureSettings,
    private val customLockscreenClockManager: CustomLockscreenClockManager,
) : KeyguardSection() {
    
    private var clockStyleView: ClockStyle? = null
    private var activeClockType: ClockType = ClockType.NONE
    private var currentClockStyle: Int = 0
    
    private enum class ClockType {
        NONE,           // No custom clock (value 0)
        CLOCK_STYLE,    // RisingOS ClockStyle (values 1-17)
        AVIUM_EXTENDED  // Avium CustomClock (values 24-27)
    }
    
    private val customClockView: View?
        get() = customLockscreenClockManager.view
    
    companion object {
        // Clock style ranges
        private const val CLOCK_DISABLED = 0
        private const val RISING_OS_CLOCK_MIN = 1
        private const val RISING_OS_CLOCK_MAX = 16
        private const val AVIUM_EXTENDED_START = 24
        private const val AVIUM_EXTENDED_END = 27
        
        // Avium extended clock mappings (settings value -> factory type)
        private val AVIUM_EXTENDED_MAPPING = mapOf(
            24 to 18,  // NType
            25 to 19,  // NDot
            26 to 20,  // Graphic
            27 to 22   // LondonUG
        )
    }
    
    /**
     * Safely sets a system property with error handling
     */
    private fun setSystemPropertySafe(key: String, value: String): Boolean {
        return try {
            android.os.SystemProperties.set(key, value)
            Log.d(TAG, "Successfully set $key=$value")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set system property $key=$value", e)
            false
        }
    }
    
    /**
     * Maps clock_style values to Avium extended clock types
     */
    private fun mapToAviumExtendedClockType(clockStyle: Int): Int {
        return AVIUM_EXTENDED_MAPPING[clockStyle] ?: run {
            Log.w(TAG, "Unknown clock style: $clockStyle, defaulting to NType")
            18  // Default to NType
        }
    }
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        // Read clock style from settings
        currentClockStyle = secureSettings.getIntForUser(
            ClockStyle.CLOCK_STYLE_KEY, CLOCK_DISABLED, UserHandle.USER_CURRENT
        )
        
        Log.d(TAG, "Clock style value: $currentClockStyle")
        
        when {
            currentClockStyle == CLOCK_DISABLED -> {
                // No custom clock
                activeClockType = ClockType.NONE
                Log.d(TAG, "No custom clock enabled")
            }
            currentClockStyle in RISING_OS_CLOCK_MIN..RISING_OS_CLOCK_MAX -> {
                // RisingOS clock style (1-16)
                activeClockType = ClockType.CLOCK_STYLE
                addRisingOSClockStyle(constraintLayout)
                Log.d(TAG, "Using RisingOS clock style: $currentClockStyle")
            }
            currentClockStyle in AVIUM_EXTENDED_START..AVIUM_EXTENDED_END -> {
                // Avium extended clock (24-27)
                activeClockType = ClockType.AVIUM_EXTENDED
                val aviumClockType = mapToAviumExtendedClockType(currentClockStyle)
                val customView = getAviumExtendedClock(aviumClockType, currentClockStyle)
                if (customView != null) {
                    addAviumExtendedClock(constraintLayout, customView)
                    Log.d(TAG, "Using Avium extended clock type: $aviumClockType (style: $currentClockStyle)")
                } else {
                    activeClockType = ClockType.NONE
                    Log.w(TAG, "Failed to create Avium extended clock for style: $currentClockStyle")
                }
            }
            else -> {
                // Invalid value or handled by CustomClockSection (17-23)
                activeClockType = ClockType.NONE
                Log.d(TAG, "Clock style not handled by this section: $currentClockStyle")
            }
        }
    }
    
    /**
     * Gets Avium extended clock by setting the clock type in system properties
     */
    private fun getAviumExtendedClock(aviumClockType: Int, clockStyle: Int): View? {
        // Enable custom lockscreen and set the type
        val enableSuccess = setSystemPropertySafe("persist.avium.customlockscreen.enable", "true")
        val typeSuccess = setSystemPropertySafe("persist.avium.customlockscreen.type", aviumClockType.toString())
        
        if (!enableSuccess || !typeSuccess) {
            Log.w(TAG, "Failed to set system properties for Avium extended clock, attempting to continue anyway")
        }
        
        // Get the view from the manager
        return customClockView
    }
    
    private fun addAviumExtendedClock(constraintLayout: ConstraintLayout, view: View) {
        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
        
        val oldParent = view.parent
        if (oldParent is ViewGroup) {
            oldParent.removeView(view)
        }
        
        constraintLayout.addView(
            view,
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
    
    private fun addRisingOSClockStyle(constraintLayout: ConstraintLayout) {
        // Disable Avium custom lockscreen
        setSystemPropertySafe("persist.avium.customlockscreen.enable", "false")
        
        // Remove existing clock style view if it exists
        constraintLayout.findViewById<View?>(R.id.clock_ls)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        // Inflate the clock style layout
        val inflater = android.view.LayoutInflater.from(context)
        clockStyleView = inflater.inflate(R.layout.keyguard_clock_style, null) as ClockStyle
        clockStyleView?.apply {
            id = R.id.clock_ls
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            visibility = View.VISIBLE
        }
        
        clockStyleView?.let { constraintLayout.addView(it) }
    }
    
    override fun bindData(constraintLayout: ConstraintLayout) {
        when (activeClockType) {
            ClockType.CLOCK_STYLE -> {
                // Trigger an update for the RisingOS clock view
                clockStyleView?.let { clockView ->
                    clockView.onTimeChanged()
                    clockView.requestLayout()
                }
            }
            ClockType.AVIUM_EXTENDED -> {
                // Avium extended clock handles its own binding
                Log.d(TAG, "Avium extended clock binding handled by manager")
            }
            ClockType.NONE -> {
                // No action needed
            }
        }
    }
    
    override fun applyConstraints(constraintSet: ConstraintSet) {
        when (activeClockType) {
            ClockType.AVIUM_EXTENDED -> applyAviumExtendedClockConstraints(constraintSet)
            ClockType.CLOCK_STYLE -> applyRisingOSClockStyleConstraints(constraintSet)
            ClockType.NONE -> {} // No constraints to apply
        }
    }
    
    private fun applyAviumExtendedClockConstraints(constraintSet: ConstraintSet) {
        val view = customClockView ?: return
        
        constraintSet.apply {
            constrainWidth(view.id, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(view.id, ConstraintSet.MATCH_CONSTRAINT)
            connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }
    
    private fun applyRisingOSClockStyleConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            // Position the custom clock within the keyguard_status_area
            connect(
                R.id.clock_ls,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.clock_ls,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            // Position custom clock at the top of status area with minimal margin
            val topMargin = (context.resources.getDimensionPixelSize(R.dimen.status_bar_height) * 1.25f).toInt()
            connect(
                R.id.clock_ls,
                ConstraintSet.TOP,
                ConstraintSet.PARENT_ID,
                ConstraintSet.TOP,
                topMargin
            )
            
            // Ensure other elements are positioned below the custom clock
            if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                connect(
                    R.id.keyguard_slice_view,
                    ConstraintSet.TOP,
                    R.id.clock_ls,
                    ConstraintSet.BOTTOM,
                    context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start)
                )
            }
            
            if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                // Position weather below slice view if it exists, otherwise below clock
                if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                    connect(
                        R.id.keyguard_weather,
                        ConstraintSet.TOP,
                        R.id.keyguard_slice_view,
                        ConstraintSet.BOTTOM,
                        8
                    )
                } else {
                    connect(
                        R.id.keyguard_weather,
                        ConstraintSet.TOP,
                        R.id.clock_ls,
                        ConstraintSet.BOTTOM,
                        8
                    )
                }
            }
            
            // Set dimensions
            constrainHeight(R.id.clock_ls, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.clock_ls, ConstraintSet.MATCH_CONSTRAINT)
            
            // Set side margins to 0 to match default clock positioning
            setMargin(R.id.clock_ls, ConstraintSet.START, 0)
            setMargin(R.id.clock_ls, ConstraintSet.END, 0)
            
            // Update the barrier to include custom clock for proper notification positioning
            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *intArrayOf(
                    R.id.keyguard_slice_view,
                    R.id.keyguard_weather,
                    R.id.clock_ls
                )
            )
            
            // Ensure notification icons are positioned below all status area content
            if (constraintSet.getConstraint(R.id.left_aligned_notification_icon_container) != null) {
                connect(
                    R.id.left_aligned_notification_icon_container,
                    ConstraintSet.TOP,
                    R.id.smart_space_barrier_bottom,
                    ConstraintSet.BOTTOM,
                    context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                )
            }
            
            // Set proper elevation within the status area
            setElevation(R.id.clock_ls, 1f)
        }
    }
    
    override fun removeViews(constraintLayout: ConstraintLayout) {
        when (activeClockType) {
            ClockType.AVIUM_EXTENDED -> {
                customClockView?.let { constraintLayout.removeView(it) }
                // Disable Avium custom lockscreen
                setSystemPropertySafe("persist.avium.customlockscreen.enable", "false")
            }
            ClockType.CLOCK_STYLE -> {
                clockStyleView?.let { clockView ->
                    (clockView.parent as? ViewGroup)?.removeView(clockView)
                }
                clockStyleView = null
            }
            ClockType.NONE -> {} // Nothing to remove
        }
        activeClockType = ClockType.NONE
    }
}

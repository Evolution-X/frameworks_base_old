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
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.clocks.ClockStyle
import com.android.systemui.customization.R as custR
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import com.android.systemui.util.settings.SecureSettings
import javax.inject.Inject

class KeyguardClockStyleSection
@Inject
constructor(
    private val context: Context,
    private val secureSettings: SecureSettings,
) : KeyguardSection() {
    
    private var clockStyleView: ClockStyle? = null
    private var isCustomClockEnabled: Boolean = false
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        // Check if custom clock is enabled
        val clockStyle = secureSettings.getIntForUser(
            ClockStyle.CLOCK_STYLE_KEY, 0, UserHandle.USER_CURRENT
        )
        isCustomClockEnabled = clockStyle != 0
        
        if (!isCustomClockEnabled) return
        
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
        // Trigger an update for the clock view to ensure it displays
        clockStyleView?.let { clockView ->
            clockView.onTimeChanged()
            // Force a layout pass
            clockView.requestLayout()
        }
    }
    
    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled || !isCustomClockEnabled) return
        
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
            // Use a small margin to avoid status bar overlap but keep it close to top
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
                    R.id.clock_ls,
                    R.id.keyguard_info_widgets
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
        clockStyleView?.let { clockView ->
            (clockView.parent as? ViewGroup)?.removeView(clockView)
        }
        clockStyleView = null
    }
}

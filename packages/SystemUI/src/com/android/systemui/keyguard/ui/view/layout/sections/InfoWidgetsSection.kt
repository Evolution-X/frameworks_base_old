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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import javax.inject.Inject

class InfoWidgetsSection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    private var infoWidgetsView: View? = null
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        // Remove existing view with the same ID if it exists
        constraintLayout.findViewById<View?>(R.id.keyguard_info_widgets)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        // Inflate the info widgets layout
        infoWidgetsView = LayoutInflater.from(context).inflate(
            R.layout.keyguard_info_widgets,
            constraintLayout,
            false
        ).apply {
            id = R.id.keyguard_info_widgets
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        constraintLayout.addView(infoWidgetsView)
    }
    
    override fun bindData(constraintLayout: ConstraintLayout) {
        // The ProgressImageView components handle their own data binding
        // through their onAttachedToWindow/onDetachedFromWindow lifecycle
    }
    
    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        constraintSet.apply {
            // Position info widgets within the keyguard_status_area
            connect(
                R.id.keyguard_info_widgets,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.keyguard_info_widgets,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            // Position below the weather view or clock_ls if available
            if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_weather,
                    ConstraintSet.BOTTOM,
                    8 // Small margin
                )
            } else if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.clock_ls,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else {
                // Last resort: position below the small clock
                connect(
                    R.id.keyguard_info_widgets,
                    ConstraintSet.TOP,
                    R.id.lockscreen_clock_view,
                    ConstraintSet.BOTTOM,
                    8
                )
            }
            
            // Set dimensions
            constrainHeight(R.id.keyguard_info_widgets, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.keyguard_info_widgets, ConstraintSet.MATCH_CONSTRAINT)
            
            // Set appropriate margins matching the XML structure
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.START, 0)
            setMargin(R.id.keyguard_info_widgets, ConstraintSet.END, 0)
            
            // Ensure proper layering within the status area
            setElevation(R.id.keyguard_info_widgets, 1f)
            
            // Update the barrier to include info widgets for proper notification positioning
            // This ensures notifications appear below all status area content
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
            
            // Ensure notification icons are positioned below the barrier
            if (constraintSet.getConstraint(R.id.left_aligned_notification_icon_container) != null) {
                connect(
                    R.id.left_aligned_notification_icon_container,
                    ConstraintSet.TOP,
                    R.id.smart_space_barrier_bottom,
                    ConstraintSet.BOTTOM,
                    context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
                )
            }
        }
    }
    
    override fun removeViews(constraintLayout: ConstraintLayout) {
        infoWidgetsView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        infoWidgetsView = null
    }
}

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
 *
 */
package com.android.systemui.keyguard.ui.view.layout.sections

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import javax.inject.Inject
import com.android.systemui.lockscreen.LockScreenWidgets

class KeyguardWidgetViewSection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {

    private var widgetView: LockScreenWidgets? = null

    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        constraintLayout.findViewById<View?>(R.id.keyguard_widgets)?.let { existingView ->
            // Remove from current parent if it exists
            (existingView.parent as? ViewGroup)?.removeView(existingView)
            
            // Add to constraint layout
            constraintLayout.addView(existingView)
            
            // Cast to LockScreenWidgets and store reference
            widgetView = existingView as? LockScreenWidgets
            
            // Ensure proper layout parameters
            existingView.layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        // Data binding is handled through the controller pattern
        // The view handles initialization through its lifecycle methods
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        constraintSet.apply {
            // Position widgets within the keyguard_status_area, below other status area content
            connect(
                R.id.keyguard_widgets,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.keyguard_widgets,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            // Position below the info widgets if available, otherwise below other status content
            if (constraintSet.getConstraint(R.id.keyguard_info_widgets) != null) {
                connect(
                    R.id.keyguard_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_info_widgets,
                    ConstraintSet.BOTTOM,
                    8 // Small margin
                )
            } else if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                connect(
                    R.id.keyguard_widgets,
                    ConstraintSet.TOP,
                    R.id.clock_ls,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                connect(
                    R.id.keyguard_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_weather,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                connect(
                    R.id.keyguard_widgets,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else {
                // Last resort: position below the small clock
                connect(
                    R.id.keyguard_widgets,
                    ConstraintSet.TOP,
                    R.id.lockscreen_clock_view,
                    ConstraintSet.BOTTOM,
                    8
                )
            }
            
            // Set dimensions
            constrainHeight(R.id.keyguard_widgets, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.keyguard_widgets, ConstraintSet.MATCH_CONSTRAINT)
            
            // Set margins matching the status area structure
            setMargin(R.id.keyguard_widgets, ConstraintSet.START, 0)
            setMargin(R.id.keyguard_widgets, ConstraintSet.END, 0)
            
            // Set elevation to ensure proper layering within status area
            setElevation(R.id.keyguard_widgets, 2f)
            
            // Update the barrier to include widgets for proper notification positioning
            createBarrier(
                R.id.smart_space_barrier_bottom,
                Barrier.BOTTOM,
                0,
                *intArrayOf(
                    R.id.keyguard_slice_view,
                    R.id.keyguard_weather,
                    R.id.clock_ls,
                    R.id.keyguard_info_widgets,
                    R.id.keyguard_widgets
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
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        widgetView?.let { view ->
            // The LockScreenWidgets will handle cleanup through onDetachedFromWindow
            constraintLayout.removeView(view)
        }
        widgetView = null
    }
}

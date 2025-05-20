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

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.Barrier
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.notifications.ui.PeekDisplayView
import com.android.systemui.notifications.ui.PeekDisplayHolderLinearLayout
import com.android.systemui.res.R
import javax.inject.Inject

class KeyguardPeekDisplaySection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    companion object {
        private const val TAG = "KeyguardPeekDisplaySection"
        private const val PEEK_DISPLAY_LOCATION_TOP = 0
        private const val PEEK_DISPLAY_LOCATION_BOTTOM = 1
    }
    
    // Top peek display components
    private var peekDisplayHolderTop: PeekDisplayHolderLinearLayout? = null
    private var peekDisplayTopView: PeekDisplayView? = null
    
    // Bottom peek display components
    private var peekDisplayHolderBottom: PeekDisplayHolderLinearLayout? = null
    private var peekDisplayBottomView: PeekDisplayView? = null
    
    // Settings
    private var peekDisplayEnabled = false
    private var peekDisplayLocation = PEEK_DISPLAY_LOCATION_BOTTOM
    private var contentObserver: ContentObserver? = null

    private fun registerContentObserver(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "registerContentObserver called")
        val handler = Handler(context.mainLooper)
        contentObserver = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                Log.d(TAG, "Settings changed, updating peek display state")
                updatePeekDisplayState(constraintLayout)
            }
        }
        val contentResolver: ContentResolver = context.contentResolver
        
        // Register observers for both settings
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_notifications"),
            false,
            contentObserver!!
        )
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("peek_display_location"),
            false,
            contentObserver!!
        )
    }
    
    private fun unregisterContentObserver() {
        Log.d(TAG, "unregisterContentObserver called")
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }
    
    private fun updatePeekDisplayState(constraintLayout: ConstraintLayout) {
        peekDisplayEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1
        
        peekDisplayLocation = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", PEEK_DISPLAY_LOCATION_BOTTOM, UserHandle.USER_CURRENT
        )
        
        Log.d(TAG, "updatePeekDisplayState - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")
        
        // Update visibility and constraints based on settings
        updatePeekDisplayVisibility()
        applyLocationConstraints(constraintLayout)
    }
    
    private fun updatePeekDisplayVisibility() {
        Log.d(TAG, "updatePeekDisplayVisibility - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")
        
        if (!peekDisplayEnabled) {
            Log.d(TAG, "Peek display disabled, hiding both views")
            peekDisplayHolderTop?.visibility = View.GONE
            peekDisplayHolderBottom?.visibility = View.GONE
            return
        }
        
        // Show only the view for the selected location
        when (peekDisplayLocation) {
            PEEK_DISPLAY_LOCATION_TOP -> {
                Log.d(TAG, "Setting visibility - top: true, bottom: false")
                peekDisplayHolderTop?.visibility = View.VISIBLE
                peekDisplayHolderBottom?.visibility = View.GONE
                peekDisplayTopView?.updatePeekDisplayState()
            }
            PEEK_DISPLAY_LOCATION_BOTTOM -> {
                Log.d(TAG, "Setting visibility - top: false, bottom: true")
                peekDisplayHolderTop?.visibility = View.GONE
                peekDisplayHolderBottom?.visibility = View.VISIBLE
                peekDisplayBottomView?.updatePeekDisplayState()
            }
        }
    }

    private fun applyLocationConstraints(constraintLayout: ConstraintLayout) {
        // Re-apply constraints when location changes
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)
        applyConstraints(constraintSet)
        constraintSet.applyTo(constraintLayout)
    }

    override fun addViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "addViews called - MigrateClocksToBlueprint.isEnabled: ${MigrateClocksToBlueprint.isEnabled}")
        
        if (!MigrateClocksToBlueprint.isEnabled) return

        // Get current settings
        peekDisplayEnabled = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_notifications", 0, UserHandle.USER_CURRENT
        ) == 1

        peekDisplayLocation = Settings.Secure.getIntForUser(
            context.contentResolver,
            "peek_display_location", PEEK_DISPLAY_LOCATION_BOTTOM, UserHandle.USER_CURRENT
        )

        Log.d(TAG, "Initial settings - enabled: $peekDisplayEnabled, location: $peekDisplayLocation")

        try {
            // Create both top and bottom peek display views
            createTopPeekDisplay(constraintLayout)
            createBottomPeekDisplay(constraintLayout)
            
            // Set initial visibility based on settings
            updatePeekDisplayVisibility()
            
            // Register content observer to handle settings changes
            registerContentObserver(constraintLayout)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in addViews", e)
        }
    }

    private fun createTopPeekDisplay(constraintLayout: ConstraintLayout) {
        // Remove existing view with the same ID if it exists
        constraintLayout.findViewById<View?>(R.id.peek_display_area_top)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        // Create the top PeekDisplayHolderLinearLayout
        peekDisplayHolderTop = PeekDisplayHolderLinearLayout(context).apply {
            id = R.id.peek_display_area_top
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Create top PeekDisplayView
        val peekDisplayView = PeekDisplayView(context).apply {
            id = R.id.peek_display_top
        }
        
        peekDisplayHolderTop?.addView(peekDisplayView)
        peekDisplayTopView = peekDisplayView
        
        constraintLayout.addView(peekDisplayHolderTop)
        Log.d(TAG, "Added top peek display views to constraint layout")
    }

    private fun createBottomPeekDisplay(constraintLayout: ConstraintLayout) {
        // Remove existing view with the same ID if it exists
        constraintLayout.findViewById<View?>(R.id.peek_display_area_bottom)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        // Create the bottom PeekDisplayHolderLinearLayout
        peekDisplayHolderBottom = PeekDisplayHolderLinearLayout(context).apply {
            id = R.id.peek_display_area_bottom
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // Create bottom PeekDisplayView
        val peekDisplayView = PeekDisplayView(context).apply {
            id = R.id.peek_display_bottom
        }
        
        peekDisplayHolderBottom?.addView(peekDisplayView)
        peekDisplayBottomView = peekDisplayView
        
        constraintLayout.addView(peekDisplayHolderBottom)
        Log.d(TAG, "Added bottom peek display views to constraint layout")
    }

    override fun bindData(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "bindData called")
        try {
            // Update the peek display state to ensure it's correctly initialized
            if (peekDisplayEnabled) {
                when (peekDisplayLocation) {
                    PEEK_DISPLAY_LOCATION_TOP -> peekDisplayTopView?.updatePeekDisplayState()
                    PEEK_DISPLAY_LOCATION_BOTTOM -> peekDisplayBottomView?.updatePeekDisplayState()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bindData", e)
        }
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        Log.d(TAG, "applyConstraints called")
        
        if (!MigrateClocksToBlueprint.isEnabled) return

        try {
            // Apply constraints for top peek display
            applyTopPeekDisplayConstraints(constraintSet)
            
            // Apply constraints for bottom peek display
            applyBottomPeekDisplayConstraints(constraintSet)
            
            // Update barrier to include active peek display
            updateSmartSpaceBarrier(constraintSet)
            
            Log.d(TAG, "Constraints applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error in applyConstraints", e)
        }
    }

    private fun applyTopPeekDisplayConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            // Position top peek display within the keyguard_status_area
            connect(
                R.id.peek_display_area_top,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.peek_display_area_top,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            // Position below widgets if available, otherwise below other status content
            if (constraintSet.getConstraint(R.id.keyguard_widgets) != null) {
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.TOP,
                    R.id.keyguard_widgets,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_info_widgets) != null) {
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.TOP,
                    R.id.keyguard_info_widgets,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else if (constraintSet.getConstraint(R.id.clock_ls) != null) {
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.TOP,
                    R.id.clock_ls,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_weather) != null) {
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.TOP,
                    R.id.keyguard_weather,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else if (constraintSet.getConstraint(R.id.keyguard_slice_view) != null) {
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.TOP,
                    R.id.keyguard_slice_view,
                    ConstraintSet.BOTTOM,
                    8
                )
            } else {
                // Last resort: position below the small clock
                connect(
                    R.id.peek_display_area_top,
                    ConstraintSet.TOP,
                    R.id.lockscreen_clock_view,
                    ConstraintSet.BOTTOM,
                    8
                )
            }
            
            // Set dimensions
            constrainHeight(R.id.peek_display_area_top, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.peek_display_area_top, ConstraintSet.MATCH_CONSTRAINT)
            
            // Set margins
            setMargin(R.id.peek_display_area_top, ConstraintSet.START, 0)
            setMargin(R.id.peek_display_area_top, ConstraintSet.END, 0)
            
            // Set elevation
            setElevation(R.id.peek_display_area_top, 3f)
        }
    }

    private fun applyBottomPeekDisplayConstraints(constraintSet: ConstraintSet) {
        constraintSet.apply {
            // Position bottom peek display
            connect(
                R.id.peek_display_area_bottom,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.peek_display_area_bottom,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            
            // Position above bottom area elements
            if (constraintSet.getConstraint(R.id.keyguard_indication_area) != null) {
                connect(
                    R.id.peek_display_area_bottom,
                    ConstraintSet.BOTTOM,
                    R.id.keyguard_indication_area,
                    ConstraintSet.TOP,
                    16
                )
            } else if (constraintSet.getConstraint(R.id.start_button) != null) {
                connect(
                    R.id.peek_display_area_bottom,
                    ConstraintSet.BOTTOM,
                    R.id.start_button,
                    ConstraintSet.TOP,
                    16
                )
            } else {
                // Position above parent bottom as fallback
                connect(
                    R.id.peek_display_area_bottom,
                    ConstraintSet.BOTTOM,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.BOTTOM,
                    64
                )
            }
            
            // Set dimensions
            constrainHeight(R.id.peek_display_area_bottom, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.peek_display_area_bottom, ConstraintSet.MATCH_CONSTRAINT)
            
            // Set margins
            setMargin(R.id.peek_display_area_bottom, ConstraintSet.START, 0)
            setMargin(R.id.peek_display_area_bottom, ConstraintSet.END, 0)
            
            // Set elevation
            setElevation(R.id.peek_display_area_bottom, 3f)
        }
    }

    private fun updateSmartSpaceBarrier(constraintSet: ConstraintSet) {
        // Create barrier that includes the active peek display location
        val barrierIds = mutableListOf<Int>().apply {
            add(R.id.keyguard_slice_view)
            add(R.id.keyguard_weather)
            add(R.id.clock_ls)
            add(R.id.keyguard_info_widgets)
            add(R.id.keyguard_widgets)
            
            // Add the appropriate peek display based on location
            if (peekDisplayLocation == PEEK_DISPLAY_LOCATION_TOP) {
                add(R.id.peek_display_area_top)
            }
            // Note: Bottom peek display shouldn't be included in smart_space_barrier_bottom
            // as it's positioned at the bottom of the screen
        }
        
        constraintSet.createBarrier(
            R.id.smart_space_barrier_bottom,
            Barrier.BOTTOM,
            0,
            *barrierIds.toIntArray()
        )
        
        // Ensure notification icons are positioned below the barrier
        if (constraintSet.getConstraint(R.id.left_aligned_notification_icon_container) != null) {
            constraintSet.connect(
                R.id.left_aligned_notification_icon_container,
                ConstraintSet.TOP,
                R.id.smart_space_barrier_bottom,
                ConstraintSet.BOTTOM,
                context.resources.getDimensionPixelSize(R.dimen.below_clock_padding_start_icons)
            )
        }
    }

    override fun removeViews(constraintLayout: ConstraintLayout) {
        Log.d(TAG, "removeViews called")
        
        // Unregister content observer
        unregisterContentObserver()
        
        try {
            // Remove both holder views from the layout
            peekDisplayHolderTop?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                Log.d(TAG, "Removed top peek display holder view")
            }
            
            peekDisplayHolderBottom?.let { view ->
                (view.parent as? ViewGroup)?.removeView(view)
                Log.d(TAG, "Removed bottom peek display holder view")
            }
            
            // Clear references
            peekDisplayHolderTop = null
            peekDisplayTopView = null
            peekDisplayHolderBottom = null
            peekDisplayBottomView = null
        } catch (e: Exception) {
            Log.e(TAG, "Error in removeViews", e)
        }
    }
}

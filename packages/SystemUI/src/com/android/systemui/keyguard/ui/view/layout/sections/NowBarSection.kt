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
import android.database.ContentObserver
import android.os.Handler
import android.os.UserHandle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.keyguard.NowBarHolder
import com.android.systemui.keyguard.MigrateClocksToBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.res.R
import javax.inject.Inject

class NowBarSection
@Inject
constructor(
    private val context: Context,
) : KeyguardSection() {
    
    private var nowBarHolder: NowBarHolder? = null
    private var marginBottom = 18 // Default margin in dp
    private var isEnabled = false
    
    private val contentObserver = object : ContentObserver(Handler()) {
        override fun onChange(selfChange: Boolean) {
            updateSettings()
            updateMarginAndVisibility()
        }
    }
    
    override fun addViews(constraintLayout: ConstraintLayout) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        // Initialize settings
        updateSettings()
        
        // Remove existing view with the same ID if it exists (regardless of type)
        constraintLayout.findViewById<View?>(R.id.now_bar_area)?.let { existingView ->
            (existingView.parent as? ViewGroup)?.removeView(existingView)
        }
        
        // Create and add new NowBarHolder
        nowBarHolder = NowBarHolder(context).apply {
            id = R.id.now_bar_area
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(marginBottom)
            }
            visibility = if (isEnabled) View.VISIBLE else View.GONE
        }
        
        constraintLayout.addView(nowBarHolder)
        registerSettingsObserver()
    }
    
    override fun bindData(constraintLayout: ConstraintLayout) {
        // Update visibility and margin based on current settings
        updateMarginAndVisibility()
    }
    
    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!MigrateClocksToBlueprint.isEnabled) return
        
        constraintSet.apply {
            // Position the now bar at the bottom of the screen
            connect(
                R.id.now_bar_area,
                ConstraintSet.START,
                ConstraintSet.PARENT_ID,
                ConstraintSet.START
            )
            connect(
                R.id.now_bar_area,
                ConstraintSet.END,
                ConstraintSet.PARENT_ID,
                ConstraintSet.END
            )
            connect(
                R.id.now_bar_area,
                ConstraintSet.BOTTOM,
                ConstraintSet.PARENT_ID,
                ConstraintSet.BOTTOM
            )
            
            // Set height to wrap content
            constrainHeight(R.id.now_bar_area, ConstraintSet.WRAP_CONTENT)
            constrainWidth(R.id.now_bar_area, ConstraintSet.MATCH_CONSTRAINT)
        }
    }
    
    override fun removeViews(constraintLayout: ConstraintLayout) {
        unregisterSettingsObserver()
        nowBarHolder?.let { holder ->
            (holder.parent as? ViewGroup)?.removeView(holder)
        }
        nowBarHolder = null
    }
    
    private fun updateSettings() {
        val contentResolver = context.contentResolver
        
        isEnabled = Settings.System.getIntForUser(
            contentResolver,
            "keyguard_now_bar_enabled",
            0,
            UserHandle.USER_CURRENT
        ) != 0
        
        marginBottom = Settings.System.getIntForUser(
            contentResolver,
            "nowbar_margin_bottom",
            18,
            UserHandle.USER_CURRENT
        )
    }
    
    private fun updateMarginAndVisibility() {
        nowBarHolder?.let { holder ->
            holder.post {
                // Update visibility
                holder.visibility = if (isEnabled) View.VISIBLE else View.GONE
                
                // Update margin
                val params = holder.layoutParams as? ConstraintLayout.LayoutParams
                params?.let {
                    it.bottomMargin = dpToPx(marginBottom)
                    holder.layoutParams = it
                }
            }
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
    
    private fun registerSettingsObserver() {
        val contentResolver = context.contentResolver
        
        contentResolver.registerContentObserver(
            Settings.System.getUriFor("keyguard_now_bar_enabled"),
            false,
            contentObserver,
            UserHandle.USER_CURRENT
        )
        
        contentResolver.registerContentObserver(
            Settings.System.getUriFor("nowbar_margin_bottom"),
            false,
            contentObserver,
            UserHandle.USER_CURRENT
        )
    }
    
    private fun unregisterSettingsObserver() {
        context.contentResolver.unregisterContentObserver(contentObserver)
    }
}

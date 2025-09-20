/*
 * Copyright (C) 2025 the AviumOS Android Project
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
package org.avium.systemui.lockscreen.sections

import android.content.Context
import android.os.UserHandle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.android.systemui.clocks.ClockStyle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.util.settings.SecureSettings
import org.avium.systemui.lockscreen.CustomLockscreenClockManager
import javax.inject.Inject

private const val TAG = "AVIUM_LOCKSCREEN"

/**
 * Handles Avium custom clocks (18-23) with custom notification styling
 * 
 * This section is separate from KeyguardClockStyleSection because Avium clocks
 * have different notification layout requirements and styling.
 * 
 * Clock Style Values:
 * - 17: Avium SmallCute Clock
 * - 18: Avium ThinLong Clock
 * - 19: Avium MoreMoreThin Clock
 * - 20: Avium NormalTime Clock
 * - 21: Avium Guoguo2 Clock
 * - 22: Avium Guoguo3 Clock
 * - 23: Avium Guoguo4 Clock
 * - (24-27: Handled by KeyguardClockStyleSection)
 */
@SysUISingleton
class CustomClockSection @Inject constructor(
    private val context: Context,
    private val secureSettings: SecureSettings,
    private val customLockscreenClockManager: CustomLockscreenClockManager
) : KeyguardSection() {

    private var currentClockStyle: Int = 0
    
    companion object {
        private const val CLOCK_DISABLED = 0
        private const val AVIUM_CLOCK_START = 17
        private const val AVIUM_CLOCK_END = 23
        
        // Avium clock mappings (settings value -> factory type)
        private val AVIUM_CLOCK_MAPPING = mapOf(
            17 to 2,   // SmallCute
            18 to 5,   // ThinLong
            19 to 6,   // MoreMoreThin
            20 to 8,   // NormalTime
            21 to 12,  // Guoguo2
            22 to 13,  // Guoguo3
            23 to 14   // Guoguo4
        )
    }

    private val customView: View?
        get() = customLockscreenClockManager.view

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
     * Maps clock_style values to Avium clock types
     */
    private fun mapToAviumClockType(clockStyle: Int): Int {
        return AVIUM_CLOCK_MAPPING[clockStyle] ?: run {
            Log.w(TAG, "Unknown clock style: $clockStyle, defaulting to SmallCute")
            2  // Default to SmallCute
        }
    }
    
    private fun shouldShowAviumClock(): Boolean {
        return currentClockStyle in AVIUM_CLOCK_START..AVIUM_CLOCK_END
    }

    override fun addViews(parent: ConstraintLayout) {
        // Read clock style from settings
        currentClockStyle = secureSettings.getIntForUser(
            ClockStyle.CLOCK_STYLE_KEY, CLOCK_DISABLED, UserHandle.USER_CURRENT
        )
        
        if (!shouldShowAviumClock()) {
            Log.d(TAG, "Not showing Avium clock (style: $currentClockStyle)")
            return
        }
        
        // Map to Avium clock type and configure system properties
        val aviumClockType = mapToAviumClockType(currentClockStyle)
        val enableSuccess = setSystemPropertySafe("persist.avium.customlockscreen.enable", "true")
        val typeSuccess = setSystemPropertySafe("persist.avium.customlockscreen.type", aviumClockType.toString())
        
        if (!enableSuccess || !typeSuccess) {
            Log.w(TAG, "Failed to set system properties for Avium clock, attempting to continue anyway")
        }
        
        val view = customView ?: run {
            Log.w(TAG, "Failed to get Avium clock view for style: $currentClockStyle")
            return
        }

        if (view.id == View.NO_ID) {
            view.id = View.generateViewId()
        }
        
        val oldParent = view.parent
        if (oldParent is ViewGroup) {
            oldParent.removeView(view)
        }
        
        parent.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        Log.d(TAG, "Added Avium custom clock type: $aviumClockType (style: $currentClockStyle)")
    }

    override fun removeViews(parent: ConstraintLayout) {
        customView?.let { 
            parent.removeView(it)
            Log.d(TAG, "Removed Avium custom clock view")
        }
        // Disable Avium custom lockscreen when removing
        setSystemPropertySafe("persist.avium.customlockscreen.enable", "false")
    }

    override fun bindData(parent: ConstraintLayout) {
        if (!shouldShowAviumClock()) return
        
        // Avium custom clock handles its own data binding
        Log.d(TAG, "Avium clock binding handled by manager")
    }

    override fun applyConstraints(constraintSet: ConstraintSet) {
        if (!shouldShowAviumClock()) return
        
        val view = customView ?: return

        // Full screen constraints for Avium clocks
        // This allows the custom clock to control its own layout including notifications
        constraintSet.apply {
            constrainWidth(view.id, ConstraintSet.MATCH_CONSTRAINT)
            constrainHeight(view.id, ConstraintSet.MATCH_CONSTRAINT)
            
            connect(view.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(view.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(view.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(view.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }
}

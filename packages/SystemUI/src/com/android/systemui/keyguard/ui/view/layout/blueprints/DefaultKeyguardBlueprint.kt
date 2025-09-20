/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.systemui.keyguard.ui.view.layout.blueprints

import android.content.Context
import android.os.UserHandle
import android.util.Log
import com.android.systemui.clocks.ClockStyle
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.keyguard.shared.model.KeyguardBlueprint
import com.android.systemui.keyguard.shared.model.KeyguardSection
import com.android.systemui.keyguard.ui.view.layout.sections.AccessibilityActionsSection
import com.android.systemui.keyguard.ui.view.layout.sections.AodBurnInSection
import com.android.systemui.keyguard.ui.view.layout.sections.AodNotificationIconsSection
import com.android.systemui.keyguard.ui.view.layout.sections.AodPromotedNotificationSection
import com.android.systemui.keyguard.ui.view.layout.sections.ClockSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultDeviceEntrySection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultIndicationAreaSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultIndicationAreaTopSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultNotificationStackScrollLayoutSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultSettingsPopupMenuSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultShortcutsSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultStatusBarSection
import com.android.systemui.keyguard.ui.view.layout.sections.DefaultUdfpsAccessibilityOverlaySection
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardSectionsModule.Companion.KEYGUARD_AMBIENT_INDICATION_AREA_SECTION
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardSliceViewSection
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardClockStyleSection
import com.android.systemui.keyguard.ui.view.layout.sections.KeyguardWeatherViewSection
import com.android.systemui.keyguard.ui.view.layout.sections.SmartspaceSection
import com.android.systemui.util.settings.SecureSettings
import org.avium.systemui.lockscreen.CustomLockscreenClockManager
import org.avium.systemui.lockscreen.sections.CustomClockSection
import org.avium.systemui.lockscreen.CustomLockscreenRepository
import java.util.Optional
import javax.inject.Inject
import javax.inject.Named
import kotlin.jvm.optionals.getOrNull

private const val TAG = "DefaultKeyguardBlueprint"

/**
 * Positions elements of the lockscreen to the default position.
 *
 * Serves as a default example for [KeyguardBlueprint].
 */
@SysUISingleton
@JvmSuppressWildcards
class DefaultKeyguardBlueprint
@Inject
constructor(
    private val context: Context,
    private val secureSettings: SecureSettings,
    private val accessibilityActionsSection: AccessibilityActionsSection,
    private val defaultIndicationAreaSection: DefaultIndicationAreaSection,
    private val defaultIndicationAreaTopSection: DefaultIndicationAreaTopSection,
    private val defaultDeviceEntrySection: DefaultDeviceEntrySection,
    private val defaultShortcutsSection: DefaultShortcutsSection,
    @Named(KEYGUARD_AMBIENT_INDICATION_AREA_SECTION)
    private val defaultAmbientIndicationAreaSection: Optional<KeyguardSection>,
    private val defaultSettingsPopupMenuSection: DefaultSettingsPopupMenuSection,
    private val defaultStatusBarSection: DefaultStatusBarSection,
    private val defaultNotificationStackScrollLayoutSection: DefaultNotificationStackScrollLayoutSection,
    private val aodNotificationIconsSection: AodNotificationIconsSection,
    private val aodBurnInSection: AodBurnInSection,
    private val aodPromotedNotificationSection: AodPromotedNotificationSection,
    private val clockSection: ClockSection,
    private val smartspaceSection: SmartspaceSection,
    private val keyguardSliceViewSection: KeyguardSliceViewSection,
    private val keyguardClockStyleSection: KeyguardClockStyleSection,
    private val keyguardWeatherViewSection: KeyguardWeatherViewSection,
    private val udfpsAccessibilityOverlaySection: DefaultUdfpsAccessibilityOverlaySection,
    private val customLockscreenClockManager: CustomLockscreenClockManager,
    private val customClockSection: CustomClockSection,
    private val customLockscreenRepository: CustomLockscreenRepository,
) : KeyguardBlueprint {
    override val id: String = DEFAULT

    companion object {
        const val DEFAULT = "default"
        private const val CLOCK_DISABLED = 0
        private const val AVIUM_CLOCK_START = 17
        private const val AVIUM_CLOCK_END = 23
    }

    override val sections: List<KeyguardSection>
        get() {
            val clockStyle = secureSettings.getIntForUser(
                ClockStyle.CLOCK_STYLE_KEY, CLOCK_DISABLED, UserHandle.USER_CURRENT
            )

            val isAviumClock = clockStyle in AVIUM_CLOCK_START..AVIUM_CLOCK_END

            Log.d(TAG, "Building sections for clock style: $clockStyle, isAviumClock: $isAviumClock")

            if (isAviumClock && customLockscreenRepository.isEnabled.value) {
                Log.d("AVIUM_BLUEPRINT", "Custom lockscreen enabled. Replacing native sections.")
                
                val allSections = listOfNotNull(
                    accessibilityActionsSection,
                    defaultIndicationAreaSection,
                    defaultShortcutsSection,
                    defaultAmbientIndicationAreaSection.getOrNull(),
                    defaultSettingsPopupMenuSection,
                    defaultStatusBarSection,
                    defaultNotificationStackScrollLayoutSection,
                    aodNotificationIconsSection,
                    smartspaceSection,
                    aodBurnInSection,
                    clockSection,
                    keyguardSliceViewSection,
                    defaultDeviceEntrySection,
                    udfpsAccessibilityOverlaySection, // Add LAST: Intentionally has z-order above others
                )

                return allSections.filterNot {
                    it is ClockSection || it is SmartspaceSection || it is KeyguardSliceViewSection
                } + customClockSection
            }

            return listOfNotNull(
                accessibilityActionsSection,
                defaultIndicationAreaSection,
                if (!isAviumClock) defaultIndicationAreaTopSection else null,
                defaultShortcutsSection,
                defaultAmbientIndicationAreaSection.getOrNull(),
                defaultSettingsPopupMenuSection,
                defaultStatusBarSection,
                defaultNotificationStackScrollLayoutSection,
                aodNotificationIconsSection,
                aodPromotedNotificationSection,
                if (!isAviumClock) smartspaceSection else null,
                aodBurnInSection,
                if (!isAviumClock) clockSection else null,
                if (!isAviumClock) keyguardSliceViewSection else null,
                if (!isAviumClock) keyguardClockStyleSection else null,
                if (isAviumClock) customClockSection else null,
                if (!isAviumClock) keyguardWeatherViewSection else null,
                defaultDeviceEntrySection,
                udfpsAccessibilityOverlaySection, // Add LAST: Intentionally has z-order above others
            )
        }
}

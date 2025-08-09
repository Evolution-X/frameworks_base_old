/*
 * SPDX-FileCopyrightText: DerpFest AOSP
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.keyguard.data.quickaffordance

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import com.android.systemui.animation.Expandable
import com.android.systemui.common.shared.model.ContentDescription
import com.android.systemui.common.shared.model.Icon
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@SysUISingleton
class NowPlayingKeyguardQuickAffordanceConfig
@Inject
constructor(
    @Application private val context: Context
) : KeyguardQuickAffordanceConfig {

    override val key: String
        get() = BuiltInKeyguardQuickAffordanceKeys.NOW_PLAYING

    override fun pickerName(): String = context.getString(R.string.accessibility_now_playing_button)

    override val pickerIconResourceId: Int
        get() = R.drawable.ic_now_playing

    override val lockScreenState: Flow<KeyguardQuickAffordanceConfig.LockScreenState>
        get() =
            flowOf(
                KeyguardQuickAffordanceConfig.LockScreenState.Visible(
                    icon =
                        Icon.Resource(
                            R.drawable.ic_now_playing,
                            ContentDescription.Resource(R.string.accessibility_now_playing_button)
                        )
                )
            )

    override suspend fun getPickerScreenState(): KeyguardQuickAffordanceConfig.PickerScreenState {
        return KeyguardQuickAffordanceConfig.PickerScreenState.Default()
    }

    override fun onTriggered(
        expandable: Expandable?,
    ): KeyguardQuickAffordanceConfig.OnTriggeredResult {
        // Try Google's ambient music recognition service first (most direct approach)
        val ambientMusicIntent = Intent("com.google.intelligence.sense.ambientmusic.ondemand.AOD_CLICK").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (context.packageManager.resolveActivity(ambientMusicIntent, 0) != null) {
            return KeyguardQuickAffordanceConfig.OnTriggeredResult.StartActivity(
                intent = ambientMusicIntent,
                canShowWhileLocked = true,
            )
        }
        
        // Fallback to Google Sound Search
        val soundSearchIntent = Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (context.packageManager.resolveActivity(soundSearchIntent, 0) != null) {
            return KeyguardQuickAffordanceConfig.OnTriggeredResult.StartActivity(
                intent = soundSearchIntent,
                canShowWhileLocked = true,
            )
        }
        
        // Final fallback to Google Assistant
        val assistantIntent = Intent(Intent.ACTION_ASSIST).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (context.packageManager.resolveActivity(assistantIntent, 0) != null) {
            return KeyguardQuickAffordanceConfig.OnTriggeredResult.StartActivity(
                intent = assistantIntent,
                canShowWhileLocked = true,
            )
        }
        
        // Last resort: voice search with music prompt
        val voiceSearchIntent = Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What song is playing?")
            putExtra(RecognizerIntent.EXTRA_SECURE, true)
        }
        return KeyguardQuickAffordanceConfig.OnTriggeredResult.StartActivity(
            intent = voiceSearchIntent,
            canShowWhileLocked = true,
        )
    }
}

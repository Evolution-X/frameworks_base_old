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
 
package com.android.systemui.qs.tiles

import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.widget.SeekBar
import com.android.internal.logging.MetricsLogger
import com.android.internal.logging.nano.MetricsProto.MetricsEvent
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.VolumeController
import javax.inject.Inject

class VolumeQSTile @Inject constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val ctl: VolumeController
) : QSTileImpl<BooleanState>(
    host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
    statusBarStateController, activityStarter, qsLogger
), VolumeController.VolumeListener {

    companion object {
        const val TILE_SPEC = "volume"
    }
    
    private var muted = false

    private val am: AudioManager = 
        mContext.getSystemService(AudioManager::class.java)

    override fun handleSetListening(listening: Boolean) {
        super.handleSetListening(listening)
        if (listening) {
            ctl.addListener(this)
        } else {
            ctl.removeListener(this)
        }
    }

    override fun handleDestroy() {
        super.handleDestroy()
        ctl.removeListener(this)
    }

    override fun newTileState(): BooleanState {
        return BooleanState().apply {
            handlesLongClick = false
        }
    }

    override fun getLongClickIntent(): Intent {
        return Intent(Settings.ACTION_SOUND_SETTINGS)
    }

    override fun isAvailable(): Boolean {
        return am != null
    }

    override fun handleClick(expandable: Expandable?) {
        ctl.expandDialog(expandable)
    }

    override fun getTileLabel(): CharSequence {
        return mContext.getString(R.string.quick_settings_volume_label)
    }

    override fun handleLongClick(expandable: Expandable?) {
        val intent = longClickIntent
        mActivityStarter.postStartActivityDismissingKeyguard(intent, 0)
    }

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        state.label = mHost.context.getString(R.string.quick_settings_volume_label)

        val percent = if (ctl.max > 0 && !muted) {
            (ctl.current * 100f / ctl.max).toInt()
        } else {
            0
        }

        state.value = percent > 0
        state.secondaryLabel = "$percent%"
        state.stateDescription = state.secondaryLabel
        state.contentDescription = mContext.getString(R.string.quick_settings_volume_label) + ", $percent%"
        when {
            percent <= 0 -> {
                state.state = Tile.STATE_INACTIVE
                state.icon = maybeLoadResourceIcon(R.drawable.ic_volume_media_mute)
            }
            else -> {
                state.state = Tile.STATE_ACTIVE
                state.icon = maybeLoadResourceIcon(R.drawable.ic_volume_media)
            }
        }
    }

    override fun getMetricsCategory(): Int {
        return MetricsEvent.QS_PANEL
    }

    override fun onVolumeChanged(volume: Int) {
        refreshState(volume)
    }

    override fun onMuteStateChanged(muted: Boolean) {
        this.muted = muted
        refreshState()
    }
}

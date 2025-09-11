/*
 * Copyright (C) 2014 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.systemui.qs.tiles;

import android.app.ActivityManager;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.service.quicksettings.Tile;
import android.widget.Switch;

import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;
import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.BooleanState;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;
import com.android.systemui.statusbar.policy.FlashlightController;
import com.android.systemui.statusbar.policy.FlashlightStrengthController;

import javax.inject.Inject;

/**
 * Quick settings tile: Control flashlight
 **/
public class FlashlightTile extends QSTileImpl<BooleanState> implements
        FlashlightController.FlashlightListener {

    public static final String TILE_SPEC = "flashlight";

    private final FlashlightController mFlashlightController;
    private final FlashlightStrengthController mStrengthController;

    @Inject
    public FlashlightTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger,
            FlashlightController flashlightController,
            FlashlightStrengthController flashlightStrengthController
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mFlashlightController = flashlightController;
        mFlashlightController.observe(getLifecycle(), this);
        mStrengthController = flashlightStrengthController;
    }
    
    @Override
    public void handleSetListening(boolean listening) {
        super.handleSetListening(listening);
        setListening(listening);
    }

    @Override
    protected void handleDestroy() {
        super.handleDestroy();
        setListening(false);
    }

    @Override
    public BooleanState newTileState() {
        BooleanState state = new BooleanState();
        state.handlesLongClick = true;
        state.handlesSecondaryClick = true;
        return state;
    }

    @Override
    public Intent getLongClickIntent() {
        if (mStrengthController.getSupported()) {
            return null;
        }
        return new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
    }

    @Override
    public boolean isAvailable() {
        return mFlashlightController.hasFlashlight();
    }

    @Override
    protected void handleClick(@Nullable Expandable expandable) {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }
        boolean newState = !mState.value;
        if (mStrengthController.getSupported()) {
            mStrengthController.expandDialog(expandable);
        } else {
            mFlashlightController.setFlashlight(newState);
        }
        refreshState(newState);
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_flashlight_label);
    }

    @Override
    protected void handleLongClick(@Nullable Expandable expandable) {
        if (mStrengthController.getSupported()) {
            mStrengthController.expandDialog(expandable);
        } else {
            handleClick(expandable);
        }
    }

    @Override
    protected void handleSecondaryClick(@Nullable Expandable expandable) {
        if (mStrengthController.getSupported()) {
            mStrengthController.handleClick();
        } else {
            handleClick(expandable);
        }
    }

    @Override
    protected void handleUpdateState(BooleanState state, Object arg) {
        state.label = mHost.getContext().getString(R.string.quick_settings_flashlight_label);
        state.secondaryLabel = "";
        state.stateDescription = "";
        if (!mFlashlightController.isAvailable()) {
            state.secondaryLabel = mContext.getString(
                    R.string.quick_settings_flashlight_camera_in_use);
            state.stateDescription = state.secondaryLabel;
            state.state = Tile.STATE_UNAVAILABLE;
            state.icon = maybeLoadResourceIcon(R.drawable.qs_flashlight_icon_off);
            return;
        }
        if (mStrengthController.getSupported()) {
            int torchLvl = mStrengthController.getTorchLevel();
            boolean enabled = mFlashlightController.isEnabled();
            int percent = mStrengthController.getLastPercent();
            boolean showPercent = enabled && torchLvl > 0;
            state.secondaryLabel = showPercent ? percent + "%" : "";
            state.stateDescription = showPercent ? state.secondaryLabel : "";
            state.contentDescription = mContext.getString(R.string.quick_settings_flashlight_label);
            state.expandedAccessibilityClassName = Switch.class.getName();
            state.state = enabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
            state.icon = maybeLoadResourceIcon(enabled
                    ? R.drawable.qs_flashlight_icon_on
                    : R.drawable.qs_flashlight_icon_off);
            return;
        }
        if (arg instanceof Boolean) {
            boolean value = (Boolean) arg;
            if (value == state.value) {
                return;
            }
            state.value = value;
        } else {
            state.value = mFlashlightController.isEnabled();
        }
        state.contentDescription = mContext.getString(R.string.quick_settings_flashlight_label);
        state.expandedAccessibilityClassName = Switch.class.getName();
        state.state = state.value ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE;
        state.icon = maybeLoadResourceIcon(state.value
                ? R.drawable.qs_flashlight_icon_on
                : R.drawable.qs_flashlight_icon_off);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_FLASHLIGHT;
    }

    @Override
    public void onFlashlightChanged(boolean enabled) {
        refreshState(enabled);
    }

    @Override
    public void onFlashlightError() {
        refreshState(false);
    }

    @Override
    public void onFlashlightAvailabilityChanged(boolean available) {
        refreshState();
    }
    
    private void setListening(boolean listening) {
        if (!mStrengthController.getSupported()) return;
        FlashlightStrengthController.OnTorchLevelChangedListener r = !listening ? null : level -> {
            refreshState();
        };
        mStrengthController.setListener(r);
    }
}

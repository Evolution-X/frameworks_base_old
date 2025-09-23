/*
 * Copyright (C) 2022-2023 The Nameless-AOSP Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.telephony.PhoneStateListener;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyDisplayInfo;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.animation.Expandable;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.dagger.qualifiers.Main;
import com.android.systemui.plugins.ActivityStarter;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.qs.QSTile.State;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.QSHost;
import com.android.systemui.qs.QsEventLogger;
import com.android.systemui.qs.logging.QSLogger;
import com.android.systemui.qs.tileimpl.QSTileImpl;
import com.android.systemui.res.R;

import java.util.List;

import javax.inject.Inject;

public class PreferredNetworkTile extends QSTileImpl<State> {
    public static final String TILE_SPEC = "preferred_network";

    private static final String TAG = "PreferredNetworkTile";
    private static final boolean DEBUG = false;

    private static final int TYPE_UNKNOWN = 0;
    private static final int TYPE_2G = 1;
    private static final int TYPE_3G = 2;
    private static final int TYPE_4G = 3;
    private static final int TYPE_5G = 4;

    private final TelephonyManager mTelephonyManager;
    private final SubscriptionManager mSubscriptionManager;
    private int mSimCount = 0;
    private boolean mCanSwitch = true;

    private TelephonyDisplayInfo mTelephonyDisplayInfo;

    @Inject
    public PreferredNetworkTile(
            QSHost host,
            QsEventLogger uiEventLogger,
            @Background Looper backgroundLooper,
            @Main Handler mainHandler,
            FalsingManager falsingManager,
            MetricsLogger metricsLogger,
            StatusBarStateController statusBarStateController,
            ActivityStarter activityStarter,
            QSLogger qsLogger
    ) {
        super(host, uiEventLogger, backgroundLooper, mainHandler, falsingManager, metricsLogger,
                statusBarStateController, activityStarter, qsLogger);
        mTelephonyManager = mContext.getSystemService(TelephonyManager.class);
        mSubscriptionManager = mContext.getSystemService(SubscriptionManager.class);
        updateSimCount();
    }

    @Override
    public boolean isAvailable() {
        return mTelephonyManager.getPhoneType() != TelephonyManager.PHONE_TYPE_NONE;
    }

    @Override
    public State newTileState() {
        return new State();
    }

    @Override
    public void handleClick(@Nullable Expandable expandable) {
        if (!mCanSwitch || mSimCount == 0) return;

        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) return;

        TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
        int current = getCurrentType(tm);
        if (current == TYPE_UNKNOWN) return;

        int next = getNextType(tm, current);
        long mask = getMaskForType(next);

        tm.setAllowedNetworkTypesForReason(
            TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER, mask);
        if (DEBUG) Log.d(TAG, "Applied type=" + next + " mask=" + mask);

        refreshState();
    }

    @Override
    public Intent getLongClickIntent() {
        if (mSimCount == 0) return null;

        Intent intent = new Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS);
        int dataSub = SubscriptionManager.getDefaultDataSubscriptionId();
        if (dataSub != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            intent.putExtra(Settings.EXTRA_SUB_ID, dataSub);
        }

        return intent;
    }

    @Override
    protected void handleUpdateState(State state, Object arg) {
        updateSimCount();
        state.icon = ResourceIcon.get(R.drawable.ic_preferred_network);
        state.label = mContext.getString(R.string.quick_settings_preferred_network_label);

        if (mSimCount == 0) {
            state.state = Tile.STATE_UNAVAILABLE;
            state.secondaryLabel = mContext.getString(
                    R.string.quick_settings_preferred_network_unsupported);
            return;
        }

        int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        TelephonyManager tm = mTelephonyManager.createForSubscriptionId(subId);
        
        int current = getCurrentType(tm);
        state.state = current == TYPE_UNKNOWN ? Tile.STATE_UNAVAILABLE : Tile.STATE_ACTIVE;
        switch (current) {
            case TYPE_2G: state.secondaryLabel = "2G"; break;
            case TYPE_3G: state.secondaryLabel = "3G"; break;
            case TYPE_4G:
                if (mTelephonyDisplayInfo != null) {
                    switch (mTelephonyDisplayInfo.getOverrideNetworkType()) {
                        case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA:
                        case TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED:
                            state.secondaryLabel = "5G";
                            break;
                        default: state.secondaryLabel = "4G";
                    }
                } else {
                   state.secondaryLabel = "4G";
                }
                break;
            case TYPE_5G: state.secondaryLabel = "5G"; break;
            default:
                state.secondaryLabel = mContext.getString(
                        R.string.quick_settings_preferred_network_unsupported);
        }
    }

    @Override
    public CharSequence getTileLabel() {
        return mContext.getString(R.string.quick_settings_preferred_network_label);
    }

    @Override
    public int getMetricsCategory() {
        return MetricsEvent.QS_PANEL;
    }

    @Override
    public void handleSetListening(boolean listening) {
        if (listening) {
            mTelephonyManager.listen(mPhoneStateListener,
                    PhoneStateListener.LISTEN_CALL_STATE | PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED);
        } else {
            mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        }
    }

    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String phoneNumber) {
            mCanSwitch = (state == TelephonyManager.CALL_STATE_IDLE);
            refreshState();
        }

        @Override
        public void onDisplayInfoChanged(@NonNull TelephonyDisplayInfo displayInfo) {
            mTelephonyDisplayInfo = displayInfo;
            refreshState();
        }
    };

    private long getMaskForType(int type) {
        switch (type) {
            case TYPE_2G: return TelephonyManager.NETWORK_CLASS_BITMASK_2G;
            case TYPE_3G: return TelephonyManager.NETWORK_CLASS_BITMASK_3G;
            case TYPE_4G: return TelephonyManager.NETWORK_CLASS_BITMASK_4G;
            case TYPE_5G: return TelephonyManager.NETWORK_CLASS_BITMASK_5G;
            default: return 0;
        }
    }

    private int getCurrentType(TelephonyManager tm) {
        long allowed = tm.getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_USER);

        for (int type : new int[]{TYPE_5G, TYPE_4G, TYPE_3G, TYPE_2G}) {
            if ((allowed & getMaskForType(type)) != 0) {
                return type;
            }
        }

        return TYPE_UNKNOWN;
    }

    private int getNextType(TelephonyManager tm, int currentType) {
        int[] order = {TYPE_2G, TYPE_3G, TYPE_4G, TYPE_5G};
        int idx = 0;
        long supported = tm.getAllowedNetworkTypesForReason(
                TelephonyManager.ALLOWED_NETWORK_TYPES_REASON_CARRIER);

        for (int i = 0; i < order.length; i++) {
            if (order[i] == currentType) {
                idx = i;
                break;
            }
        }

        for (int i = 1; i <= order.length; i++) {
            int nextType = order[(idx + i) % order.length];
            long mask = getMaskForType(nextType);
            if ((supported & mask) != 0) {
                return nextType;
            }
        }

        return currentType;
    }

    private void updateSimCount() {
        List<SubscriptionInfo> list = mSubscriptionManager.getActiveSubscriptionInfoList();
        mSimCount = (list != null) ? list.size() : 0;
        if (DEBUG) Log.d(TAG, "updateSimCount = " + mSimCount);
    }
}

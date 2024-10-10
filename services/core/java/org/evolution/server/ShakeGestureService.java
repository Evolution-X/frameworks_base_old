/*
 * Copyright (C) 2023-2024 The RisingOS Android Project
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
package org.evolution.server;

import android.content.Context;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;

public final class ShakeGestureService {

    private static final String TAG = "ShakeGestureService";

    private static final String SHAKE_GESTURES_ENABLED = "shake_gestures_enabled";
    private static final String SHAKE_GESTURES_ACTION = "shake_gestures_action";
    private static final int USER_ALL = UserHandle.USER_ALL;

    private final Context mContext;
    private ShakeGestureUtils mShakeGestureUtils;
    private final ShakeGesturesCallbacks mShakeCallbacks;
    private final SettingsObserver mSettingsObserver;

    private boolean mShakeServiceEnabled = false;
    private int mShakeAction = 0;

    private ShakeGestureUtils.OnShakeListener mShakeListener;

    public interface ShakeGesturesCallbacks {
        void onShake();
    }

    public ShakeGestureService(Context context, ShakeGesturesCallbacks callback) {
        mContext = context;
        mShakeCallbacks = callback;
        mShakeGestureUtils = new ShakeGestureUtils(mContext);
        mSettingsObserver = new SettingsObserver(null);
    }
    
    public void systemReady() {
        mShakeListener = () -> {
            if (mShakeServiceEnabled && mShakeCallbacks != null) {
                mShakeCallbacks.onShake();
            }
        };
    }
    
    public int getAction() {
        return mShakeAction;
    }

    class SettingsObserver extends ContentObserver {
        SettingsObserver(Handler handler) {
            super(handler);
            observe();
            updateSettings();
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(SHAKE_GESTURES_ENABLED), false, this, USER_ALL);
            mContext.getContentResolver().registerContentObserver(
                    Settings.Secure.getUriFor(SHAKE_GESTURES_ACTION), false, this, USER_ALL);
        }
        
        private void updateSettings() {
            boolean wasEnabled = mShakeServiceEnabled && mShakeAction != 0;

            mShakeServiceEnabled = Settings.Secure.getInt(
                    mContext.getContentResolver(), SHAKE_GESTURES_ENABLED, 0) == 1;
            mShakeAction = Settings.Secure.getInt(
                    mContext.getContentResolver(), SHAKE_GESTURES_ACTION, 0);

            boolean isEnabled = mShakeServiceEnabled && mShakeAction != 0;

            if (isEnabled == wasEnabled) return;

            if (isEnabled) {
                mShakeGestureUtils.registerListener(mShakeListener);
            } else {
                mShakeGestureUtils.unregisterListener(mShakeListener);
            }
        }

        @Override
        public void onChange(boolean selfChange) {
            updateSettings();
        }
    }
}

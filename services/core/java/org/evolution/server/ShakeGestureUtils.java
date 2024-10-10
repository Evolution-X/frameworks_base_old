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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.provider.Settings;

import java.util.ArrayList;

public class ShakeGestureUtils implements SensorEventListener {

    private static final String TAG = "ShakeGestureUtils";

    private static final String SHAKE_GESTURES_SHAKE_INTENSITY = "shake_gestures_intensity";

    private Context mContext;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private ArrayList<OnShakeListener> mListeners = new ArrayList<>();
    private long mLastShakeTime = 0L;
    private long mLastUpdateTime = 0L;
    private int mShakeCount = 0;
    private float mLastX = 0f;
    private float mLastY = 0f;
    private float mLastZ = 0f;

    public ShakeGestureUtils(Context context) {
        mContext = context;
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (mSensorManager != null) {
            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    public interface OnShakeListener {
        void onShake();
    }

    public void registerListener(OnShakeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
            startListening();
        }
    }

    public void unregisterListener(OnShakeListener listener) {
        mListeners.remove(listener);
        if (mListeners.isEmpty()) {
            stopListening();
        }
    }

    private void startListening() {
        if (mAccelerometer != null) {
            mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    private void stopListening() {
        mSensorManager.unregisterListener(this);
    }

    private int getShakeIntensity() {
        return Settings.System.getInt(mContext.getContentResolver(),
                SHAKE_GESTURES_SHAKE_INTENSITY, 3);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null) {
            return;
        }
        long curUpdateTime = System.currentTimeMillis();
        long timeInterval = curUpdateTime - mLastUpdateTime;
        if (timeInterval < (getShakeIntensity() * 14f)) {
            return;
        }
        if (event.values.length < 3) {
            return;
        }
        mLastUpdateTime = curUpdateTime;
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float deltaX = x - mLastX;
        float deltaY = y - mLastY;
        float deltaZ = z - mLastZ;
        mLastX = x;
        mLastY = y;
        mLastZ = z;
        double speed = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ) * 1000.0 / timeInterval;
        if (speed >= getShakeIntensity() * 100f) {
            notifyShakeListeners();
        }
    }

    private void notifyShakeListeners() {
        if (SystemClock.elapsedRealtime() - mLastShakeTime < 1000) {
            return;
        }
        for (OnShakeListener listener : mListeners) {
            listener.onShake();
        }
        mLastShakeTime = SystemClock.elapsedRealtime();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}

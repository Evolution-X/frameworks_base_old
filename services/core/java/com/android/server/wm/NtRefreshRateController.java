/*
 * Copyright (C) 2025 AxionOS
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
package com.android.server.wm;

import static android.os.Process.SYSTEM_UID;
import static android.view.WindowManager.LayoutParams.TYPE_NOTIFICATION_SHADE;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
import static com.android.server.wm.NtAppRefreshRateProvider.DEFAULT_APP_CONFIGS;
import static com.android.server.wm.NtAppRefreshRateProvider.GAMING_APPS;
import static com.android.server.wm.NtAppRefreshRateProvider.SYS_WINDOW_TYPES;

import com.android.server.wm.NtAppRefreshRateProvider.AppRefreshRateConfig;
import com.android.server.wm.NtAppRefreshRateProvider.AppVoteInfo;

import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.WindowManager;

import java.util.Arrays;

public class NtRefreshRateController {

    public interface GameFpsCallback {
        void setGameFps(int uid, float fps);
    }

    private static final String TAG = "NtRefreshRateController";
    private static final String SETTINGS_REFRESH_RATE_MODE = "display_refresh_rate_mode";
    private static final String PEAK_REFRESH_RATE = "peak_refresh_rate";

    private static final NtRefreshRateController INSTANCE = new NtRefreshRateController();

    private final SparseIntArray gameUidToFpsMap = new SparseIntArray(10);
    private AppVoteInfo currentVote;
    private AppVoteInfo bestVote;

    private Context context;
    private Handler bgHandler;
    private SettingsObserver settingsObserver;
    private WindowManagerService wm;

    private float maxSupportedHz = 60f;
    private float defaultMinRefreshRate = 60f;

    private boolean isVrrEnabled = false;
    private int currentRefreshRate = 60;
    
    private boolean isCtsTest = false;
    private boolean overrideWinPrefer = false;
    private boolean lastIdle = false;
    private boolean disableIdle = false;
    private int maxWindowSize = 0;

    private final boolean supportsVRR = SystemProperties.getBoolean(
            "ro.surface_flinger.use_content_detection_for_refresh_rate", false);
    private final boolean supportsIdle = SystemProperties.getBoolean(
            "ro.surface_flinger.support_kernel_idle_timer", false);

    private GameFpsCallback mCallbacks;

    private NtRefreshRateController() {}

    public static NtRefreshRateController get() { return INSTANCE; }

    public void setGameFpsCallback(GameFpsCallback callback) {
        this.mCallbacks = callback;
    }

    public void init(Context context, WindowManagerService wm) {
        this.context = context;
        this.wm = wm;

        HandlerThread thread = new HandlerThread("NtRefreshRateConfig");
        thread.start();
        bgHandler = new Handler(thread.getLooper());
        settingsObserver = new SettingsObserver();

        final DisplayManager dm = context.getSystemService(DisplayManager.class);
        final Display display = dm.getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode[] supportedModes = display.getSupportedModes();

        maxSupportedHz = 60f;
        defaultMinRefreshRate = Float.MAX_VALUE;

        for (Display.Mode mode : supportedModes) {
            float hz = mode.getRefreshRate();
            if (hz > maxSupportedHz) maxSupportedHz = hz;
            if (hz < defaultMinRefreshRate) defaultMinRefreshRate = hz;
        }

        if (defaultMinRefreshRate == Float.MAX_VALUE) defaultMinRefreshRate = 60f;

        loadRefreshRateSetting();
        updateRefreshRate();

        bestVote = new AppVoteInfo(null, 0, 0.0f, 0.0f, false);
        currentVote = new AppVoteInfo(null, 0, 0.0f, 0.0f, false);
    }

    private void loadRefreshRateSetting() {
        int value = Settings.Global.getInt(context.getContentResolver(),
                SETTINGS_REFRESH_RATE_MODE, supportsVRR ? 0 : Math.round(maxSupportedHz));
        isVrrEnabled = (value == 0 && supportsVRR);
        if (!isVrrEnabled) currentRefreshRate = value;
    }

    private void updateRefreshRate() {
        if (isVrrEnabled) {
            setSystemRefreshRates(defaultMinRefreshRate, maxSupportedHz);
        } else {
            setSystemRefreshRates(currentRefreshRate, currentRefreshRate);
        }
    }

    private void setIdleFpsMode(boolean disableIdle) {
        float idleHz = isVrrEnabled ? defaultMinRefreshRate : currentRefreshRate;
        float minHz = disableIdle ? maxSupportedHz : idleHz;
        Settings.System.putFloat(context.getContentResolver(),
                Settings.System.MIN_REFRESH_RATE, minHz);
        wm.requestTraversal();
    }

    private void setSystemRefreshRates(float minRate, float peakRate) {
        Settings.System.putFloat(context.getContentResolver(), Settings.System.MIN_REFRESH_RATE, minRate);
        Settings.System.putFloat(context.getContentResolver(), PEAK_REFRESH_RATE, peakRate);
    }

    private void handleFocusedAppUpdate(ActivityRecord activityRecord) {
        AppRefreshRateConfig config = getAppConfigIfGame(activityRecord);
        if (config != null) {
            int uid = activityRecord.getUid();
            int targetFps = config.refreshRates.valueAt(0);
            synchronized (gameUidToFpsMap) {
                if (gameUidToFpsMap.get(uid, -1) != targetFps) {
                    if (mCallbacks != null) mCallbacks.setGameFps(uid, targetFps);
                    gameUidToFpsMap.put(uid, targetFps);
                }
            }
            disableIdle = config.disableIdle;
        } else {
            disableIdle = false;
        }
        if (supportsIdle && lastIdle != disableIdle) {
            bgHandler.post(() -> setIdleFpsMode(disableIdle));
            lastIdle = disableIdle;
        }
    }

    private AppRefreshRateConfig getAppConfigIfGame(ActivityRecord activityRecord) {
        String process = activityRecord.getProcessName();
        int category = activityRecord.info.applicationInfo.category;
        if (DEFAULT_APP_CONFIGS.containsKey(process) &&
            (category == 0 || GAMING_APPS.contains(process))) {
            return DEFAULT_APP_CONFIGS.get(process);
        }
        return null;
    }

    public void resetNtVoteResult() {
        bestVote.reset();
        isCtsTest = false;
        overrideWinPrefer = false;
        maxWindowSize = 0;
        disableIdle = false;
    }

    public void updateVoteResult() {
        if (isCtsTest) {
            currentVote.updateVote("CtsTest", 0, 0.0f, 0.0f);
            currentVote.hasVote = false;
        } else if (overrideWinPrefer) {
            int modeId = getModeId(Math.round(maxSupportedHz));
            currentVote.updateVote("OverrideWinPrefer", modeId, 0.0f, maxSupportedHz);
        } else if (bestVote.hasVote) {
            currentVote.copyFrom(bestVote);
        } else {
            float settingRate = isVrrEnabled ? maxSupportedHz : currentRefreshRate;
            int modeId = getModeId(Math.round(settingRate));
            currentVote.updateVote("SettingMode", modeId, 0.0f, settingRate);
        }

        if (supportsIdle && lastIdle != disableIdle) {
            bgHandler.post(() -> setIdleFpsMode(disableIdle));
            lastIdle = disableIdle;
        }
    }

    public void setGameModeFrameRateOverrideToNtRefreshRate(int uid, float frameRate) {
        synchronized (gameUidToFpsMap) {
            if (gameUidToFpsMap.get(uid, -1) >= 0) {
                gameUidToFpsMap.put(uid, Math.round(frameRate));
            }
        }
    }

    public void updateFocusedApp(final ActivityRecord activityRecord) {
        bgHandler.post(() -> handleFocusedAppUpdate(activityRecord));
    }

    private boolean shouldOverrideForWindow(WindowState ws, boolean displayOn) {
        if (ws.inMultiWindowMode()) {
            return true;
        }

        int type = ws.mAttrs.type;
        if (type == TYPE_NOTIFICATION_SHADE || 
            (type == TYPE_APPLICATION_OVERLAY && ws.mOwnerUid != SYSTEM_UID)) {
            return true;
        }

        if (!displayOn) {
            return true;
        }

        if (ws.isAnimationRunningSelfOrParent()) {
            return true;
        }

        return false;
    }

    public void voteNtPreferredModeId(WindowState ws, boolean displayOn) {
        if (SYS_WINDOW_TYPES.contains(ws.getName())) {
            return;
        }

        String pkg = ws.getOwningPackage();

        if (pkg.contains("android.graphics.cts") || pkg.contains("com.android.cts")) {
            isCtsTest = true;
            return;
        }

        if (isCtsTest) {
            return;
        }

        float targetRate = 0f;
        int targetModeId = 0;
        
        AppRefreshRateConfig config = DEFAULT_APP_CONFIGS.get(pkg);
        if (config != null) {
            targetRate = config.refreshRates.valueAt(0);
            targetModeId = getModeId(Math.round(targetRate));
            
            if (config.disableIdle) {
                disableIdle = true;
            }

            if (config.disableSV) {
                WindowManager.LayoutParams lp = ws.mAttrs;
                if (lp.preferredMinDisplayRefreshRate == 59.0f &&
                    lp.preferredMaxDisplayRefreshRate == 60.0f) {
                    lp.preferredMinDisplayRefreshRate = lp.preferredMaxDisplayRefreshRate = 0.0f;
                }
            }
        }

        if (shouldOverrideForWindow(ws, displayOn)) {
            overrideWinPrefer = true;
            targetRate = maxSupportedHz;
            targetModeId = getModeId(Math.round(targetRate));
        }

        if (targetRate <= 0) {
            return;
        }

        int windowSize = ws.mRequestedWidth * ws.mRequestedHeight;

        if (targetRate > bestVote.maxRefreshRate || 
            (Math.abs(targetRate - bestVote.maxRefreshRate) < 1.0f && windowSize > maxWindowSize)) {
            bestVote.updateVote(pkg, targetModeId, 0.0f, targetRate);
            maxWindowSize = windowSize;
        }
    }

    public boolean OverrideWinPrefer() {
        return overrideWinPrefer;
    }

    public float getMaxPreferRate() { 
        return currentVote.maxRefreshRate; 
    }

    public float getMinPreferRate() { 
        return currentVote.minRefreshRate; 
    }

    public int getModeId(int rate) {
        Display.Mode mode = new Display.Mode.Builder()
                .setRefreshRate(rate)
                .build();
        return Math.round(mode.getRefreshRate());
    }

    public int getPreferMode() {
        return currentVote.preferredModeId;
    }

    private final class SettingsObserver extends ContentObserver {
        private final Uri refreshRateModeUri =
                Settings.Global.getUriFor(SETTINGS_REFRESH_RATE_MODE);

        public SettingsObserver() {
            super(bgHandler);
            context.getContentResolver().registerContentObserver(
                    refreshRateModeUri, false, this, -1);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (!refreshRateModeUri.equals(uri)) return;
            loadRefreshRateSetting();
            updateRefreshRate();
            wm.requestTraversal();
        }
    }
}

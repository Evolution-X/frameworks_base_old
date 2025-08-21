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

import android.util.ArrayMap;
import android.util.SparseIntArray;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class NtAppRefreshRateProvider {

    public static final ArrayMap<String, AppRefreshRateConfig> DEFAULT_APP_CONFIGS = new ArrayMap<>();

    public static final Set<String> GAMING_APPS = Set.of(
            "com.tencent.ig", "com.rekoo.pubgm", "com.vng.pubgmobile",
            "com.pubg.krmobile", "com.tencent.igce", "com.pubg.imobile"
    );

    public static final Set<String> SYS_WINDOW_TYPES = Set.of(
            "StatusBar", "NavigationBar", "wallpapers"
    );

    static {
        addConfig(DEFAULT_APP_CONFIGS, new String[]{
            "com.google.android.projection.gearhead.phonescreen","com.waze","com.google.android.apps.mapslite",
            "ru.yandex.yandexmaps","maps.pro","com.baidu.BaiduMap","com.mapquest.android.ace","com.autonavi.minimap",
            "com.nhn.android.nmap","com.here.app.maps","com.ss.android.ugc.aweme","com.ss.android.ugc.aweme.lite",
            "com.ss.android.ugc.trill","tv.twitch.android.app"
        }, "60,60,60", false, false);

        addConfig(DEFAULT_APP_CONFIGS, new String[]{
            "com.android.chrome","com.brave.browser","com.opera.browser","org.mozilla.firefox","org.torproject.torbrowser",
            "com.sec.android.app.sbrowser","com.kiwibrowser.browser","com.microsoft.emmx","com.hsv.freeadblockerbrowser",
            "com.vivaldi.browser","com.yandex.browser","org.adblockplus.browser","com.naver.whale"
        }, "90,120,60", true, false);

        addConfig(DEFAULT_APP_CONFIGS, new String[]{
            "com.android.launcher3","com.tencent.mm","com.google.android.apps.nbu.paisa.user","com.android.systemui",
            "com.android.wallpaper"
        }, "120,120,60", true, false);

        addConfig(DEFAULT_APP_CONFIGS, new String[]{
            "com.axlebolt.standoff2","com.riotgames.league.wildrift","com.antutu.ABenchMark",
            "com.futuremark.dmandroid.application","com.primatealbs.geekbench6"
        }, "120,120,60", true, true);

        addConfig(DEFAULT_APP_CONFIGS, new String[]{
            "com.tencent.ig","com.tencent.igfit","com.rekoo.pubgm","com.vng.pubgmobile","com.pubg.krmobile","com.tencent.igce"
        }, "90,90,60", true, true);

        addConfig(DEFAULT_APP_CONFIGS, new String[]{ "com.pubg.imobile" }, "120,120,60", true, true);

        addConfig(DEFAULT_APP_CONFIGS, new String[]{
            "com.twitter.android","com.nothing.weather","com.android.vending","com.android.settings",
            "com.google.android.googlequicksearchbox","com.android.setupwizard.overlay","com.nothing.applocker"
        }, "120,120,60", false, false);
    }

    private static void addConfig(ArrayMap<String, AppRefreshRateConfig> map, String[] packages,
                                  String rateString, boolean disableSv, boolean disableIdle) {
        SparseIntArray refreshRates = parseRefreshRates(rateString);
        for (String pkg : packages) {
            map.put(pkg, new AppRefreshRateConfig(pkg, refreshRates, disableSv, disableIdle));
        }
    }

    private static SparseIntArray parseRefreshRates(String rateString) {
        SparseIntArray refreshRates = new SparseIntArray(3);
        String[] rates = rateString.split(",");
        for (int i = 0; i < rates.length; i++) {
            refreshRates.put(i, Integer.parseInt(rates[i]));
        }
        return refreshRates;
    }

    public static class AppRefreshRateConfig {
        public final SparseIntArray refreshRates;
        public final boolean disableSV;
        public final boolean disableIdle;
        public final String packageName;

        public AppRefreshRateConfig(String packageName, SparseIntArray refreshRates,
                                    boolean disableSV, boolean disableIdle) {
            this.packageName = packageName;
            this.refreshRates = refreshRates;
            this.disableSV = disableSV;
            this.disableIdle = disableIdle;
        }
    }

    public static class AppVoteInfo {
        public int preferredModeId;
        public float minRefreshRate;
        public float maxRefreshRate;
        public String appName;
        public boolean hasVote;

        public AppVoteInfo(String appName, int modeId, float minRate, float maxRate, boolean hasVote) {
            this.appName = appName;
            this.preferredModeId = modeId;
            this.minRefreshRate = minRate;
            this.maxRefreshRate = maxRate;
            this.hasVote = hasVote;
        }

        public void updateVote(String appName, int modeId, float minRate, float maxRate) {
            this.appName = appName;
            this.preferredModeId = modeId;
            this.minRefreshRate = minRate;
            this.maxRefreshRate = maxRate;
            this.hasVote = true;
        }

        public void copyFrom(AppVoteInfo other) {
            this.appName = other.appName;
            this.preferredModeId = other.preferredModeId;
            this.minRefreshRate = other.minRefreshRate;
            this.maxRefreshRate = other.maxRefreshRate;
            this.hasVote = other.hasVote;
        }

        public void reset() {
            appName = null;
            preferredModeId = 0;
            minRefreshRate = 0.0f;
            maxRefreshRate = 0.0f;
            hasVote = false;
        }

        @Override
        public String toString() {
            return "AppVoteInfo{appName=" + appName +
                   ", preferMode=" + preferredModeId +
                   ", minRate=" + minRefreshRate +
                   ", maxRate=" + maxRefreshRate +
                   ", hasVote=" + hasVote + "}";
        }
    }
}
